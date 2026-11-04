package dev.yide.powerbridge;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.*;
import java.util.*;
/** Central role; all mutable state and GATT operations run on the main looper. */
public final class Ad350Client {
    private static UUID uuid(String id) { return UUID.fromString("0000"+id+"-0000-1000-8000-00805f9b34fb"); }
    private static final UUID SERVICE=uuid("1828"), DATA=uuid("2a63"), COMMAND=uuid("2a55"), CCCD=uuid("2902");
    public interface Listener { void onMeasurement(XdsMeasurement m); void onLost(); }
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final Map<String,BluetoothDevice> found=new HashMap<>();
    private final Map<String,ProbeState.Nearby> nearby=new HashMap<>();
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic data,command;
    private ScanCallback scanCallback;
    private boolean running, trusted, commandBusy;
    private long epoch, deadline, lastPacket;
    private int commandAttempts;
    private String target, stage="idle";
    private StartupWindow startup=new StartupWindow();
    private final ArrayDeque<BluetoothGattCharacteristic> metadataReads=new ArrayDeque<>();
    private BluetoothGattCharacteristic batteryCharacteristic,readPending;
    private boolean metadataPrepared,metadataStalled,rssiPending;
    private long readDeadline,lastBatteryPoll=-1,lastRssiPoll=-1;
    public Ad350Client(Context c,BluetoothAdapter a,Listener l) { context=c; adapter=a; listener=l; }
    public void start(String saved) { running=true; scan(saved); handler.post(watchdog); }
    private void state(String s) { ProbeState.inputStatus=s; ProbeState.log("AD350："+s); }
    public void scan(String wanted) {
        if (!running) return;
        ++epoch; handler.removeCallbacks(retry); stopScan(); closeGatt(); listener.onLost();
        target=wanted; trusted=wanted!=null; found.clear(); nearby.clear(); ProbeState.devices=Collections.emptyList();
        stage="scan"; ProbeState.scanning=true; ProbeState.inputLinked=false;
        state(wanted==null ? "转动曲柄后，选择附近的功率计" : "正在寻找上次的功率计");
        long token=epoch;
        scanCallback=new ScanCallback() {
            public void onScanResult(int type,ScanResult r) { handler.post(()->{ if(running && token==epoch && stage.equals("scan")) seen(r); }); }
            public void onBatchScanResults(List<ScanResult> list) { for(ScanResult r:list)onScanResult(0,r); }
            public void onScanFailed(int code) { handler.post(()->{ if(!running||token!=epoch)return; stopScan(); stage="idle"; state("搜索失败（"+code+"），请重新搜索"); }); }
        };
        try {
            BluetoothLeScanner scanner=adapter.getBluetoothLeScanner();
            if(scanner==null) throw new IllegalStateException("蓝牙扫描不可用");
            // Android suspends unfiltered scans while the screen is off. Reconnects
            // use the known address, independent of whether a service UUID is advertised.
            List<ScanFilter> filters=wanted==null?null:Collections.singletonList(new ScanFilter.Builder().setDeviceAddress(wanted).build());
            scanner.startScan(filters,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
            handler.postDelayed(()->{ if(running&&token==epoch&&stage.equals("scan")) {
                stopScan();
                if(wanted!=null){stage="retry";state("功率计暂未出现，保持自动重连");handler.removeCallbacks(retry);handler.postDelayed(retry,3000);}
                else {stage="idle";state(nearby.isEmpty()?"没有发现设备，请唤醒曲柄后重新搜索":"请选择功率计，或重新搜索");}
            } },30000);
        } catch(RuntimeException e) { stopScan(); stage="idle"; state("无法搜索："+e.getMessage()); }
    }
    private void seen(ScanResult r) {
        ScanRecord record=r.getScanRecord(); if(record==null)return;
        String address=r.getDevice().getAddress(),name=record.getDeviceName();
        boolean xds=record.getServiceUuids()!=null && record.getServiceUuids().contains(new ParcelUuid(SERVICE));
        if(name==null && !xds && !address.equals(target))return;
        if(name==null)name="喜德盛功率计";
        if(found.size()<40 || found.containsKey(address) || address.equals(target))found.put(address,r.getDevice());
        if(nearby.size()<40 || nearby.containsKey(address))nearby.put(address,new ProbeState.Nearby(address,name,r.getRssi(),xds));
        ArrayList<ProbeState.Nearby> list=new ArrayList<>(nearby.values());
        list.sort((a,b)->a.xds!=b.xds ? (a.xds?-1:1) : Integer.compare(b.rssi,a.rssi));
        ProbeState.devices=Collections.unmodifiableList(list);
        if(address.equals(target))select(address);
    }
    public void select(String address) {
        if(!running)return;
        BluetoothDevice device=found.get(address);
        if(device==null){state("设备已离开，请重新搜索");return;}
        ++epoch; handler.removeCallbacks(retry); stopScan(); closeGatt(); listener.onLost();
        target=address; trusted=address.equals(context.getSharedPreferences("sensor",0).getString("address",null));
        ProbeState.Nearby item=nearby.get(address); ProbeState.deviceName=item==null?"AD350 功率计":item.name;
        ProbeState.devices=Collections.emptyList(); stage="connect"; deadline=now()+20000;
        startup=new StartupWindow();
        state("正在连接 "+ProbeState.deviceName);
        try { gatt=device.connectGatt(context,false,callbacks,BluetoothDevice.TRANSPORT_LE); if(gatt==null)recover("连接未启动"); }
        catch(RuntimeException e){recover("连接失败："+e.getMessage());}
    }
    private void stopScan() {
        if(scanCallback!=null)try { BluetoothLeScanner s=adapter.getBluetoothLeScanner(); if(s!=null)s.stopScan(scanCallback); }catch(RuntimeException ignored){}
        scanCallback=null; ProbeState.scanning=false;
    }
    private void closeGatt() {
        BluetoothGatt old=gatt; gatt=null; data=command=null; commandBusy=false;
        metadataReads.clear();batteryCharacteristic=readPending=null;
        metadataPrepared=metadataStalled=rssiPending=false;lastBatteryPoll=lastRssiPoll=-1;
        ProbeState.clearSensorData();
        if(old!=null){try{old.disconnect();}catch(RuntimeException ignored){}try{old.close();}catch(RuntimeException ignored){}}
        ProbeState.inputLinked=false;
    }
    private boolean current(BluetoothGatt g) { return running&&g==gatt; }
    private void recover(String reason) {
        if(!running)return;
        ++epoch; closeGatt(); listener.onLost(); stage="retry";
        state(reason+"；稍后重新搜索");
        handler.removeCallbacks(retry); handler.postDelayed(retry,3000);
    }
    private final Runnable retry=()->{ if(running)scan(trusted?target:null); };
    private static long now(){return SystemClock.elapsedRealtime();}
    private final Runnable watchdog=new Runnable(){public void run(){
        if(!running)return;
        long t=now();
        if(gatt!=null && startup.expired(t))recover("启动后未收到有效测量数据");
        else if(gatt!=null && !stage.equals("stream") && t>=deadline)recover("连接或初始化超时");
        else if(gatt!=null && stage.equals("stream") && t-lastPacket>30000)recover("30秒未收到有效数据，请检查曲柄是否唤醒");
        if(gatt!=null&&stage.equals("stream"))pumpMetadata(gatt);
        handler.postDelayed(this,1000);
    }};
    private void sendCommand(BluetoothGatt g) {
        if(!current(g)||commandBusy||stage.equals("stream"))return;
        if(commandAttempts>=3){recover("订阅成功但没有收到测量数据");return;}
        ++commandAttempts; commandBusy=true; stage="start"; deadline=now()+12000;
        byte[] value={2,0x16,(byte)0xaa,0x10};
        try {
            boolean queued;
            if(Build.VERSION.SDK_INT>=33)queued=g.writeCharacteristic(command,value,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS;
            else { command.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); command.setValue(value); queued=g.writeCharacteristic(command); }
            ProbeState.log("AD350 启动命令 02 16 AA 10，入队="+queued);
            if(!queued)recover("启动命令无法发送");
        }catch(RuntimeException e){recover("启动命令失败："+e.getMessage());}
    }
    private void measurement(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] bytes) {
        if(!current(g)||c!=data)return;
        XdsMeasurement m=XdsMeasurement.parse(bytes);
        if(m==null){ProbeState.log("AD350 无法解析测量包，长度="+(bytes==null?0:bytes.length));return;}
        ProbeState.sensorStatus=m.error;
        if(m.error!=0){listener.onLost();state("传感器报告状态码 "+m.error+"，暂停转发");return;}
        lastPacket=now(); stage="stream"; startup.onData(); ProbeState.inputLinked=true;
        ProbeState.packets++;
        if(ProbeState.packets<=3 || ProbeState.packets%30==0){
            StringBuilder raw=new StringBuilder(); for(int i=0;i<Math.min(bytes.length,24);i++)raw.append(String.format(Locale.ROOT,"%02X",bytes[i]&255));
            ProbeState.log("AD350 RX "+raw+" → "+m.watts+"W / "+m.cadence+"rpm");
        }
        ProbeState.inputStatus="已连接 · 正在接收真实数据"; listener.onMeasurement(m);
        ProbeState.latest=m;
    }
    private void prepareMetadata(BluetoothGatt g){
        metadataPrepared=true;
        StringBuilder inventory=new StringBuilder();
        for(BluetoothGattService s:g.getServices()){
            inventory.append(s.getUuid()).append('\n');
            for(BluetoothGattCharacteristic c:s.getCharacteristics())inventory.append("  ").append(c.getUuid()).append(" properties=").append(c.getProperties()).append('\n');
        }
        ProbeState.inventory=inventory.toString();ProbeState.log("AD350 服务清单\n"+ProbeState.inventory);
        BluetoothGattService b=g.getService(uuid("180f"));
        batteryCharacteristic=b==null?null:b.getCharacteristic(uuid("2a19"));
        if(readable(batteryCharacteristic))metadataReads.add(batteryCharacteristic);else batteryCharacteristic=null;
        BluetoothGattService info=g.getService(uuid("180a"));
        if(info!=null)for(String id:new String[]{"2a29","2a24","2a26","2a27"}){BluetoothGattCharacteristic c=info.getCharacteristic(uuid(id));if(readable(c))metadataReads.add(c);}
    }
    private static boolean readable(BluetoothGattCharacteristic c){return c!=null&&(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_READ)!=0;}
    /** Read-only, serialized after startup. Telemetry never tears down a working power stream. */
    private void pumpMetadata(BluetoothGatt g){
        if(!current(g)||metadataStalled||commandBusy)return;
        long t=now();
        if(readPending!=null||rssiPending){
            if(t>=readDeadline){metadataStalled=true;metadataReads.clear();ProbeState.log("附加信息读取超时，本次连接暂停附加读取，功率转发继续");}
            return;
        }
        if(!metadataPrepared)prepareMetadata(g);
        if(metadataReads.isEmpty()&&batteryCharacteristic!=null&&(lastBatteryPoll<0||t-lastBatteryPoll>=60000))metadataReads.add(batteryCharacteristic);
        if(!metadataReads.isEmpty()){
            readPending=metadataReads.removeFirst();readDeadline=t+10000;
            if(readPending==batteryCharacteristic)lastBatteryPoll=t;
            try{if(!g.readCharacteristic(readPending)){ProbeState.log("附加信息读取未入队："+readPending.getUuid());readPending=null;}}
            catch(RuntimeException e){readPending=null;metadataStalled=true;ProbeState.log("附加信息读取暂停："+e.getMessage());}
            return;
        }
        if(lastRssiPoll<0||t-lastRssiPoll>=30000){
            lastRssiPoll=t;rssiPending=true;readDeadline=t+10000;
            try{if(!g.readRemoteRssi())rssiPending=false;}catch(RuntimeException e){rssiPending=false;metadataStalled=true;}
        }
    }
    private void metadataValue(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int status){
        if(!current(g)||c!=readPending)return;
        readPending=null;
        if(status!=BluetoothGatt.GATT_SUCCESS){ProbeState.log("附加信息不可读："+c.getUuid()+"，状态="+status);return;}
        UUID id=c.getUuid();
        if(id.equals(uuid("2a19"))){ProbeState.battery=SensorMetadata.battery(value);ProbeState.batteryAt=ProbeState.battery>=0?now():-1;ProbeState.log("AD350 电量="+(ProbeState.battery>=0?ProbeState.battery+"%":"未知"));}
        else {
            String text=SensorMetadata.text(value);
            if(id.equals(uuid("2a29")))ProbeState.manufacturer=text;
            else if(id.equals(uuid("2a24")))ProbeState.model=text;
            else if(id.equals(uuid("2a26")))ProbeState.firmware=text;
            else if(id.equals(uuid("2a27")))ProbeState.hardware=text;
            ProbeState.log("设备信息 "+id+"="+text);
        }
    }
    private final BluetoothGattCallback callbacks=new BluetoothGattCallback(){
        public void onConnectionStateChange(BluetoothGatt g,int status,int newState){handler.post(()->{
            if(!current(g))return;
            if(status==BluetoothGatt.GATT_SUCCESS&&newState==BluetoothProfile.STATE_CONNECTED){
                ProbeState.inputLinked=true; stage="discover"; deadline=now()+15000; state("已连接，正在识别私有服务");
                try{if(!g.discoverServices())recover("服务发现未启动");}catch(RuntimeException e){recover("服务发现失败");}
            }else if(status!=BluetoothGatt.GATT_SUCCESS || newState==BluetoothProfile.STATE_DISCONNECTED)recover("功率计断开（"+status+"）");
        });}
        public void onServicesDiscovered(BluetoothGatt g,int status){handler.post(()->{
            if(!current(g))return;
            if(status!=BluetoothGatt.GATT_SUCCESS){recover("服务发现失败（"+status+"）");return;}
            BluetoothGattService s=g.getService(SERVICE);
            if(s==null){trusted=false;recover("该设备没有喜德盛私有服务1828，请另选功率计");return;}
            data=s.getCharacteristic(DATA);command=s.getCharacteristic(COMMAND);
            BluetoothGattDescriptor cccd=data==null?null:data.getDescriptor(CCCD);
            if(data==null||command==null||cccd==null || (data.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)==0 || (command.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE)==0){trusted=false;recover("私有特征不匹配，保留日志以便排查");return;}
            trusted=true; context.getSharedPreferences("sensor",0).edit().putString("address",target).putString("name",ProbeState.deviceName).apply();
            try {
                if(!g.setCharacteristicNotification(data,true)){recover("无法启用通知");return;}
                stage="subscribe";deadline=now()+12000;boolean queued;
                if(Build.VERSION.SDK_INT>=33)queued=g.writeDescriptor(cccd,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)==BluetoothStatusCodes.SUCCESS;
                else{cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);queued=g.writeDescriptor(cccd);}
                if(!queued)recover("订阅请求未入队");else state("正在订阅功率和踏频");
            }catch(RuntimeException e){recover("订阅异常："+e.getMessage());}
        });}
        public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){handler.post(()->{
            if(!current(g)||!stage.equals("subscribe"))return;
            if(status!=BluetoothGatt.GATT_SUCCESS){recover("功率订阅失败（"+status+"）");return;}
            commandAttempts=0;stage="delay";deadline=now()+12000;state("已订阅，正在启动测量");handler.postDelayed(()->sendCommand(g),800);
        });}
        public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){handler.post(()->{
            if(!current(g)||c!=command)return;
            commandBusy=false;
            startup.onWriteComplete(status,now());
            if(startup.expired(now())){recover("启动命令写入失败（"+status+"）");return;}
            if(!stage.equals("stream")){
                stage="wait";
                if(status==129){deadline=now()+5000;state("启动返回81，保持订阅并等待测量数据");}
                else {deadline=now()+8000;state("测量已启动，等待曲柄数据");handler.postDelayed(()->sendCommand(g),2500);}
            }
        });}
        public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){byte[] copy=value.clone();handler.post(()->measurement(g,c,copy));}
        public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int status){byte[] copy=value==null?null:value.clone();handler.post(()->metadataValue(g,c,copy,status));}
        @SuppressWarnings("deprecation") public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int status){if(Build.VERSION.SDK_INT<33){byte[] b=c.getValue();byte[] copy=b==null?null:b.clone();handler.post(()->metadataValue(g,c,copy,status));}}
        public void onReadRemoteRssi(BluetoothGatt g,int rssi,int status){handler.post(()->{if(!current(g)||!rssiPending)return;rssiPending=false;if(status==BluetoothGatt.GATT_SUCCESS){ProbeState.rssi=rssi;ProbeState.rssiAt=now();ProbeState.log("AD350 信号="+rssi+"dBm");}});}
        @SuppressWarnings("deprecation") public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){if(Build.VERSION.SDK_INT<33){byte[] b=c.getValue();if(b!=null){byte[] copy=b.clone();handler.post(()->measurement(g,c,copy));}}}
    };
    public void stop(){running=false;++epoch;handler.removeCallbacksAndMessages(null);stopScan();closeGatt();ProbeState.devices=Collections.emptyList();listener.onLost();}
}
