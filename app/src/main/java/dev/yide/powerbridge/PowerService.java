package dev.yide.powerbridge;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.os.*;
import java.util.*;

/** Foreground owner of input central and output peripheral. All state runs on main. */
public class PowerService extends Service {
    private static UUID uuid(String id){return UUID.fromString("0000"+id+"-0000-1000-8000-00805f9b34fb");}
    private static final UUID CPS=uuid("1818"), MEASUREMENT=uuid("2a63"), CCCD=uuid("2902");
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final BridgeData readings=new BridgeData();
    private final Map<String,BluetoothDevice> peers=new HashMap<>();
    private final Map<String,byte[]> subscriptions=new HashMap<>();
    private final Map<String,Long> pending=new HashMap<>();
    private BluetoothAdapter adapter;
    private BluetoothGattServer server;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattCharacteristic measurement;
    private Ad350Client input;
    private PowerManager.WakeLock wakeLock;
    private boolean active, advertising, waitingForName, failed;
    private long nameDeadline;
    public void onCreate(){
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("bridge","功率桥接",NotificationManager.IMPORTANCE_LOW));
        IntentFilter f=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(bluetoothState,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(bluetoothState,f);
    }
    public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if("stop".equals(action)){stopSelf();return START_NOT_STICKY;}
        if(active){
            if(input!=null){if("select".equals(action))input.select(intent.getStringExtra("address"));else if("scan".equals(action))input.scan(null);}
            return START_NOT_STICKY;
        }
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,PowerService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"bridge").setSmallIcon(R.drawable.ic_power).setContentTitle("功率桥接正在运行").setContentText("AD350 → C606 Pro · 点此查看连接状态").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止",stop).build()).build();
        startForeground(1,n);
        active=ProbeState.active=true;ProbeState.fresh=false;ProbeState.sent=ProbeState.packets=0;ProbeState.cadence=-1;
        ProbeState.inputStatus="正在初始化";ProbeState.headStatus="正在准备连接";
        ProbeState.log("功率桥接0.11：真实AD350输入，无模拟数据");
        try{initialize();}catch(RuntimeException e){fail("启动失败："+e.getMessage());}
        return START_NOT_STICKY;
    }
    private void initialize(){
        BluetoothManager manager=getSystemService(BluetoothManager.class);
        adapter=manager==null?null:manager.getAdapter();
        if(adapter==null||!adapter.isEnabled()){fail("请先打开手机蓝牙");return;}
        wakeLock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PowerBridge:active");
        wakeLock.acquire();
        advertiser=adapter.getBluetoothLeAdvertiser();
        if(advertiser==null){fail("手机蓝牙广播不可用");return;}
        server=manager.openGattServer(this,callbacks);
        if(server==null){fail("无法启动码表连接服务");return;}
        BluetoothGattService s=new BluetoothGattService(CPS,BluetoothGattService.SERVICE_TYPE_PRIMARY);
        measurement=new BluetoothGattCharacteristic(MEASUREMENT,BluetoothGattCharacteristic.PROPERTY_NOTIFY,0);
        BluetoothGattDescriptor d=new BluetoothGattDescriptor(CCCD,BluetoothGattDescriptor.PERMISSION_READ|BluetoothGattDescriptor.PERMISSION_WRITE);
        d.setValue(new byte[]{0,0});measurement.addDescriptor(d);s.addCharacteristic(measurement);
        BluetoothGattCharacteristic feature=new BluetoothGattCharacteristic(uuid("2a65"),BluetoothGattCharacteristic.PROPERTY_READ,BluetoothGattCharacteristic.PERMISSION_READ);
        feature.setValue(new byte[]{8,0,0,0});s.addCharacteristic(feature);
        BluetoothGattCharacteristic location=new BluetoothGattCharacteristic(uuid("2a5d"),BluetoothGattCharacteristic.PROPERTY_READ,BluetoothGattCharacteristic.PERMISSION_READ);
        location.setValue(new byte[]{0});s.addCharacteristic(location);
        if(!server.addService(s)){fail("无法注册功率服务");return;}
        input=new Ad350Client(this,adapter,new Ad350Client.Listener(){
            public void onMeasurement(XdsMeasurement m){if(!active)return;readings.update(m,SystemClock.elapsedRealtime());ProbeState.watts=m.watts;ProbeState.cadence=m.cadence;ProbeState.fresh=true;}
            public void onLost(){readings.invalidate();ProbeState.fresh=false;}
        });
        String saved=getSharedPreferences("sensor",MODE_PRIVATE).getString("address",null);
        ProbeState.deviceName=getSharedPreferences("sensor",MODE_PRIVATE).getString("name","AD350 功率计");
        input.start(saved);handler.post(tick);
    }
    private void prepareName(){
        SharedPreferences prefs=getSharedPreferences("p515-name",MODE_PRIVATE);
        String current=adapter.getName();
        if(P715Sample.NAME.equals(current)){advertise();return;}
        if(current==null){fail("无法读取手机蓝牙名称");return;}
        // Recover an old sample name without overwriting the saved original.
        if(!"PES-P515_40971".equals(current))prefs.edit().putString("original",current).commit();
        waitingForName=true;nameDeadline=SystemClock.elapsedRealtime()+5000;
        if(!adapter.setName(P715Sample.NAME)){fail("无法设置兼容广播名称");return;}
        handler.post(nameCheck);
    }
    private final Runnable nameCheck=new Runnable(){public void run(){
        if(!active||!waitingForName)return;
        if(P715Sample.NAME.equals(adapter.getName())){waitingForName=false;advertise();}
        else if(SystemClock.elapsedRealtime()>=nameDeadline)fail("兼容广播名称未生效，请重试");
        else handler.postDelayed(this,100);
    }};
    private void advertise(){
        if(!active)return;
        try{
            AdvertiseSettings settings=new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).setConnectable(true).setTimeout(0).build();
            AdvertiseData data=new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(CPS)).addManufacturerData(0xffff,P715Sample.manufacturerData()).build();
            AdvertiseData response=new AdvertiseData.Builder().setIncludeDeviceName(true).build();
            advertiser.startAdvertising(settings,data,response,advertisingCallback);
        }catch(RuntimeException e){fail("广播失败："+e.getMessage());}
    }
    private final AdvertiseCallback advertisingCallback=new AdvertiseCallback(){
        public void onStartSuccess(AdvertiseSettings settings){handler.post(()->{if(!active)return;advertising=true;counts();ProbeState.log("P715兼容广播已启动，保留v0.8广播样本与03 00兼容");});}
        public void onStartFailure(int code){handler.post(()->{if(active)fail("码表广播失败（"+code+"）");});}
    };
    private void counts(){
        ProbeState.headConnections=peers.size();ProbeState.headSubscriptions=subscriptions.size();
        ProbeState.headStatus=!subscriptions.isEmpty()?"已连接 · 已订阅功率":!peers.isEmpty()?"已连接 · 等待订阅":advertising?"等待 C606 Pro 连接":"正在准备广播";
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(!active)return;
        long now=SystemClock.elapsedRealtime();byte[] packet=readings.packet(now);ProbeState.fresh=packet!=null;
        try{
            for(String id:new ArrayList<>(peers.keySet())){
                BluetoothDevice device=peers.get(id);
                if(pending.containsKey(id)){
                    if(now-pending.get(id)>5000){ProbeState.log("码表通知超时，等待重新连接");server.cancelConnection(device);drop(id);counts();}
                    continue;
                }
                if(packet==null||!subscriptions.containsKey(id))continue;
                pending.put(id,now);boolean ok;
                if(Build.VERSION.SDK_INT>=33)ok=server.notifyCharacteristicChanged(device,measurement,false,packet)==BluetoothStatusCodes.SUCCESS;
                else{measurement.setValue(packet);ok=server.notifyCharacteristicChanged(device,measurement,false);}
                if(!ok){pending.remove(id);ProbeState.log("功率通知未入队，下一次重试");}
            }
        }catch(RuntimeException e){fail("转发失败："+e.getMessage());return;}
        handler.postDelayed(this,1000);
    }};
    private void drop(String id){peers.remove(id);subscriptions.remove(id);pending.remove(id);}
    private void response(BluetoothDevice d,int id,int status,int offset,byte[] value){if(active&&server!=null)server.sendResponse(d,id,status,offset,value);}
    private final BluetoothGattServerCallback callbacks=new BluetoothGattServerCallback(){
        public void onServiceAdded(int status,BluetoothGattService service){handler.post(()->{if(!active)return;if(status==BluetoothGatt.GATT_SUCCESS)prepareName();else fail("码表服务注册失败（"+status+"）");});}
        public void onConnectionStateChange(BluetoothDevice d,int status,int state){handler.post(()->{
            if(!active)return;
            if(status==BluetoothGatt.GATT_SUCCESS&&state==BluetoothProfile.STATE_CONNECTED){peers.put(d.getAddress(),d);ProbeState.log("码表侧蓝牙已连接");}
            else{drop(d.getAddress());ProbeState.log("码表侧蓝牙断开，状态="+status);}counts();
        });}
        public void onCharacteristicReadRequest(BluetoothDevice d,int id,int offset,BluetoothGattCharacteristic c){handler.post(()->{
            byte[] value=c.getValue();
            if(MEASUREMENT.equals(c.getUuid())||value==null)response(d,id,BluetoothGatt.GATT_READ_NOT_PERMITTED,offset,null);
            else if(offset<0||offset>value.length)response(d,id,BluetoothGatt.GATT_INVALID_OFFSET,offset,null);
            else response(d,id,BluetoothGatt.GATT_SUCCESS,offset,Arrays.copyOfRange(value,offset,value.length));
        });}
        public void onDescriptorReadRequest(BluetoothDevice d,int id,int offset,BluetoothGattDescriptor descriptor){handler.post(()->{
            byte[] value=subscriptions.get(d.getAddress());if(value==null)value=new byte[]{0,0};
            if(!CCCD.equals(descriptor.getUuid()))response(d,id,BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,offset,null);
            else if(offset<0||offset>2)response(d,id,BluetoothGatt.GATT_INVALID_OFFSET,offset,null);
            else response(d,id,BluetoothGatt.GATT_SUCCESS,offset,Arrays.copyOfRange(value,offset,2));
        });}
        public void onDescriptorWriteRequest(BluetoothDevice d,int id,BluetoothGattDescriptor descriptor,boolean prepared,boolean responseNeeded,int offset,byte[] value){
            byte[] copy=value==null?null:value.clone();handler.post(()->{
                if(!active)return;
                int status=BluetoothGatt.GATT_SUCCESS,action=NotificationConfig.action(copy,true);
                if(prepared||!CCCD.equals(descriptor.getUuid())||!MEASUREMENT.equals(descriptor.getCharacteristic().getUuid()))status=BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
                else if(offset!=0)status=BluetoothGatt.GATT_INVALID_OFFSET;
                else if(copy==null||copy.length!=2)status=BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                else if(action<0)status=BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
                else if(action==1){peers.put(d.getAddress(),d);subscriptions.put(d.getAddress(),copy);}
                else subscriptions.remove(d.getAddress());
                if(responseNeeded)response(d,id,status,offset,null);
                ProbeState.log("码表CCCD="+Arrays.toString(copy)+"，响应="+status);counts();
            });
        }
        public void onCharacteristicWriteRequest(BluetoothDevice d,int id,BluetoothGattCharacteristic c,boolean prepared,boolean needed,int offset,byte[] value){handler.post(()->{if(needed)response(d,id,BluetoothGatt.GATT_WRITE_NOT_PERMITTED,offset,null);});}
        public void onExecuteWrite(BluetoothDevice d,int id,boolean execute){handler.post(()->response(d,id,BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,0,null));}
        public void onNotificationSent(BluetoothDevice d,int status){handler.post(()->{
            if(!active)return;
            Long time=pending.remove(d.getAddress());
            if(time!=null&&status==BluetoothGatt.GATT_SUCCESS){ProbeState.sent++;if(ProbeState.sent==1||ProbeState.sent%30==0)ProbeState.log("真实功率转发回调成功，累计="+ProbeState.sent);}
            else if(status!=BluetoothGatt.GATT_SUCCESS)ProbeState.log("码表通知错误="+status);
        });}
    };
    private final BroadcastReceiver bluetoothState=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(i.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1)==BluetoothAdapter.STATE_TURNING_OFF)fail("手机蓝牙已关闭");}};
    private void fail(String why){if(!active)return;failed=true;ProbeState.inputStatus=why;ProbeState.headStatus="桥接已暂停";ProbeState.log(why);stopSelf();}
    public void onDestroy(){
        active=ProbeState.active=false;waitingForName=false;handler.removeCallbacksAndMessages(null);
        if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
        if(input!=null)input.stop();readings.invalidate();ProbeState.fresh=false;
        try{if(advertiser!=null)advertiser.stopAdvertising(advertisingCallback);}catch(RuntimeException ignored){}
        try{if(server!=null){for(BluetoothDevice d:peers.values())server.cancelConnection(d);server.clearServices();server.close();}}catch(RuntimeException ignored){}
        server=null;peers.clear();subscriptions.clear();pending.clear();ProbeState.headConnections=ProbeState.headSubscriptions=0;
        try{
            if(adapter!=null&&adapter.isEnabled()){
                SharedPreferences prefs=getSharedPreferences("p515-name",MODE_PRIVATE);String original=prefs.getString("original",null);
                if(original!=null&&P715Sample.NAME.equals(adapter.getName()))adapter.setName(original);
                // Keep original until restoration is observed; allows recovery after a process death.
            }
        }catch(RuntimeException e){ProbeState.log("蓝牙名称恢复失败："+e.getMessage());}
        if(!failed){ProbeState.inputStatus="已停止";ProbeState.headStatus="已停止";}
        ProbeState.log("桥接已停止");unregisterReceiver(bluetoothState);stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    public IBinder onBind(Intent intent){return null;}
}
