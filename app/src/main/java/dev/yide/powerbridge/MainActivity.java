package dev.yide.powerbridge;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int BG=0xff101713,CARD=0xff1b2620,MINT=0xffc2f571,TEXT=0xfff0f5ee,MUTED=0xff91a195,LINE=0xff334238,AMBER=0xfff4c578;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView watts,rpm,live,powerHint,inputStatus,headStatus,sensorName,deviceHint,stats,logText,diagnosticToggle;
    private Button action,rescan;
    private TextView batteryValue,signalValue,leftValue,rightValue,balanceValue,angleValue,statusValue,infoValue;
    private ProgressBar balanceBar;
    private LinearLayout devices,deviceSection,diagnostics;
    private PowerTrace trace;
    private String deviceSignature="";
    private int observedPackets=-1;
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,500);}};
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String value,float size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");return t;}
    private void gap(LinearLayout p,int h){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private void add(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private LinearLayout card(){LinearLayout c=vertical();c.setPadding(dp(21),dp(14),dp(21),dp(14));c.setBackground(bg(CARD,24));return c;}
    private Button button(String title,boolean primary){Button b=new Button(this);b.setText(title);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(primary?BG:TEXT);b.setTypeface(null,Typeface.BOLD);b.setStateListAnimator(null);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(12),0,dp(12),0);b.setBackground(bg(primary?MINT:LINE,16));return b;}
    public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);scroll.setClipToPadding(false);
        LinearLayout root=vertical();root.setPadding(dp(22),dp(18),dp(22),dp(22));scroll.addView(root);setContentView(scroll);
        LinearLayout mast=row();TextView brand=text("POWER / BRIDGE",12,MINT);brand.setLetterSpacing(.18f);brand.setTypeface(null,Typeface.BOLD);mast.addView(brand,new LinearLayout.LayoutParams(0,-2,1));TextView version=text("0.12-csc-test",12,MUTED);mast.addView(version);add(root,mast);gap(root,12);
        TextView title=text("功率桥接",29,TEXT);title.setTypeface(null,Typeface.BOLD);add(root,title);gap(root,5);add(root,text("AD350 的每一瓦，传到你的码表。",14,MUTED));gap(root,17);
        LinearLayout hero=card();GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff26382a,0xff1a251e});gradient.setCornerRadius(dp(26));hero.setBackground(gradient);
        LinearLayout heroTop=row();heroTop.addView(text("实时功率",14,0xffb0c3b0),new LinearLayout.LayoutParams(0,-2,1));live=text("●  待连接",12,MUTED);heroTop.addView(live);add(hero,heroTop);gap(hero,10);
        LinearLayout number=row();number.setGravity(Gravity.BOTTOM);watts=text("—",80,TEXT);watts.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));watts.setIncludeFontPadding(false);watts.setMaxLines(1);watts.setAutoSizeTextTypeUniformWithConfiguration(44,80,2,android.util.TypedValue.COMPLEX_UNIT_SP);number.addView(watts,new LinearLayout.LayoutParams(0,dp(90),1));TextView unit=text("W",24,MINT);unit.setPadding(dp(10),0,0,dp(13));number.addView(unit);add(hero,number);
        trace=new PowerTrace();hero.addView(trace,new LinearLayout.LayoutParams(-1,dp(26)));gap(hero,11);
        View line=new View(this);line.setBackgroundColor(LINE);hero.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));gap(hero,12);
        LinearLayout bottom=row();LinearLayout cadence=vertical();cadence.addView(text("踏频",12,MUTED));gap(cadence,3);rpm=text("—  rpm",25,TEXT);cadence.addView(rpm);bottom.addView(cadence,new LinearLayout.LayoutParams(0,-2,1));powerHint=text("等待功率计\n仅转发真实数据",12,MUTED);powerHint.setGravity(Gravity.RIGHT);bottom.addView(powerHint);add(hero,bottom);add(root,hero);gap(root,15);
        LinearLayout section=row();TextView connection=text("连接状态",15,TEXT);connection.setTypeface(null,Typeface.BOLD);section.addView(connection,new LinearLayout.LayoutParams(0,-2,1));TextView change=text("更换功率计",12,MINT);change.setPadding(dp(8),dp(8),0,dp(8));change.setOnClickListener(v->{if(ProbeState.active)send("scan",null);else Toast.makeText(this,"先点击开始桥接",Toast.LENGTH_SHORT).show();});section.addView(change);add(root,section);gap(root,6);
        LinearLayout input=card();LinearLayout inputTop=row();TextView index=text("01",13,MINT);index.setTypeface(null,Typeface.BOLD);inputTop.addView(index);sensorName=text("AD350 功率计",16,TEXT);sensorName.setTypeface(null,Typeface.BOLD);sensorName.setPadding(dp(13),0,0,0);inputTop.addView(sensorName);add(input,inputTop);gap(input,9);inputStatus=text("等待开始",13,MUTED);add(input,inputStatus);add(root,input);gap(root,10);
        LinearLayout output=card();LinearLayout outTop=row();TextView index2=text("02",13,MINT);index2.setTypeface(null,Typeface.BOLD);outTop.addView(index2);TextView head=text("C606 Pro 码表",16,TEXT);head.setTypeface(null,Typeface.BOLD);head.setPadding(dp(13),0,0,0);outTop.addView(head);add(output,outTop);gap(output,9);headStatus=text("等待开始",13,MUTED);add(output,headStatus);gap(output,7);add(output,text("码表中选择：P715 49688",12,0xff748a7a));add(root,output);gap(root,16);
        action=button("开始桥接   →",true);root.addView(action,new LinearLayout.LayoutParams(-1,dp(56)));action.setOnClickListener(v->{if(ProbeState.active)stopService(new Intent(this,PowerService.class));else startBridge();});gap(root,10);
        add(root,text("先转动曲柄唤醒功率计，关闭其他占用它的 App。",12,MUTED));gap(root,22);
        LinearLayout extra=card();TextView moreTitle=text("功率计数据",17,TEXT);moreTitle.setTypeface(null,Typeface.BOLD);add(extra,moreTitle);gap(extra,16);
        LinearLayout health=row();LinearLayout bat=vertical();bat.addView(text("功率计电量",12,MUTED));gap(bat,4);batteryValue=text("—",27,MINT);bat.addView(batteryValue);health.addView(bat,new LinearLayout.LayoutParams(0,-2,1));LinearLayout sig=vertical();sig.addView(text("蓝牙信号",12,MUTED));gap(sig,4);signalValue=text("—",24,TEXT);sig.addView(signalValue);health.addView(sig,new LinearLayout.LayoutParams(0,-2,1));add(extra,health);gap(extra,20);
        add(extra,text("左右功率 · 参考数据",13,MUTED));gap(extra,9);LinearLayout sides=row();leftValue=text("左  — W",22,TEXT);rightValue=text("右  — W",22,TEXT);rightValue.setGravity(Gravity.RIGHT);sides.addView(leftValue,new LinearLayout.LayoutParams(0,-2,1));sides.addView(rightValue,new LinearLayout.LayoutParams(0,-2,1));add(extra,sides);gap(extra,10);
        balanceBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);balanceBar.setMax(100);balanceBar.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));balanceBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(LINE));extra.addView(balanceBar,new LinearLayout.LayoutParams(-1,dp(8)));gap(extra,7);balanceValue=text("等待有效数据",12,MUTED);add(extra,balanceValue);gap(extra,17);
        angleValue=text("曲柄角度（参考）   —",15,TEXT);add(extra,angleValue);gap(extra,9);statusValue=text("传感器状态   —",13,MUTED);add(extra,statusValue);gap(extra,12);add(extra,text("左右字段尚未验证为独立双边测量；角度零点未标定。",11,MUTED));add(root,extra);gap(root,14);
        LinearLayout info=card();add(info,text("设备信息",15,TEXT));gap(info,10);infoValue=text("连接后读取设备信息",13,MUTED);infoValue.setLineSpacing(dp(5),1f);add(info,infoValue);add(root,info);gap(root,18);
        deviceSection=vertical();LinearLayout deviceTop=row();TextView pick=text("选择功率计",16,TEXT);pick.setTypeface(null,Typeface.BOLD);deviceTop.addView(pick,new LinearLayout.LayoutParams(0,-2,1));rescan=button("重新搜索",false);rescan.setTextSize(12);deviceTop.addView(rescan,new LinearLayout.LayoutParams(dp(92),dp(36)));rescan.setOnClickListener(v->send("scan",null));add(deviceSection,deviceTop);gap(deviceSection,7);deviceHint=text("",12,MUTED);add(deviceSection,deviceHint);gap(deviceSection,10);devices=vertical();add(deviceSection,devices);add(root,deviceSection);
        diagnosticToggle=text("连接诊断  ＋",13,MUTED);diagnosticToggle.setPadding(0,dp(15),0,dp(15));add(root,diagnosticToggle);
        diagnostics=card();diagnostics.setVisibility(View.GONE);stats=text("",12,MUTED);add(diagnostics,stats);gap(diagnostics,12);Button copy=button("复制诊断日志",false);diagnostics.addView(copy,new LinearLayout.LayoutParams(-1,dp(40)));copy.setTextSize(13);copy.setOnClickListener(v->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Power Bridge","Power Bridge 0.12-csc-test\n"+Build.MANUFACTURER+" "+Build.MODEL+" Android "+Build.VERSION.RELEASE+"\n"+ProbeState.inputStatus+"\n"+ProbeState.headStatus+"\n"+ProbeState.logs()));Toast.makeText(this,"日志已复制，也可用 ADB 读取",Toast.LENGTH_SHORT).show();});gap(diagnostics,12);logText=text("",11,MUTED);logText.setTextIsSelectable(true);add(diagnostics,logText);add(root,diagnostics);
        diagnosticToggle.setOnClickListener(v->{boolean show=diagnostics.getVisibility()!=View.VISIBLE;diagnostics.setVisibility(show?View.VISIBLE:View.GONE);diagnosticToggle.setText(show?"连接诊断  −":"连接诊断  ＋");render();});
        render();
    }
    private void render(){
        boolean active=ProbeState.active,fresh=ProbeState.fresh;
        watts.setText(fresh?Integer.toString(ProbeState.watts):"—");rpm.setText(fresh&&ProbeState.cadence>=0?ProbeState.cadence+"  rpm":"—  rpm");
        live.setText(fresh?"●  实时":active?"●  连接中":"●  待连接");live.setTextColor(fresh?MINT:active?AMBER:MUTED);
        powerHint.setText(fresh?(ProbeState.headSubscriptions>0?"真实数据\n正在转发至码表":"真实数据\n等待码表连接"):active&&ProbeState.packets>0?"数据已过期\n已暂停转发":"等待功率计\n仅转发真实数据");
        inputStatus.setText(active&&!fresh&&ProbeState.inputLinked&&ProbeState.inputStatus.startsWith("已连接 · 正在接收")?"暂未收到最新数据 · 已暂停转发":ProbeState.inputStatus);
        inputStatus.setTextColor(fresh?MINT:MUTED);sensorName.setText(ProbeState.deviceName);headStatus.setText(ProbeState.headStatus);headStatus.setTextColor(ProbeState.headSubscriptions>0?MINT:MUTED);
        action.setText(active?"停止桥接":"开始桥接   →");action.setBackground(bg(active?LINE:MINT,16));action.setTextColor(active?TEXT:BG);
        if(observedPackets!=ProbeState.packets){if(ProbeState.packets<observedPackets)trace.reset();observedPackets=ProbeState.packets;if(fresh)trace.point(ProbeState.watts);}
        trace.setFresh(fresh);
        long now=SystemClock.elapsedRealtime();
        batteryValue.setText(ProbeState.inputLinked&&SensorMetadata.fresh(ProbeState.batteryAt,now,120000)?ProbeState.battery+"%":"—");
        signalValue.setText(ProbeState.inputLinked&&SensorMetadata.fresh(ProbeState.rssiAt,now,60000)?ProbeState.rssi+" dBm":"—");
        XdsMeasurement m=fresh?ProbeState.latest:null;
        leftValue.setText("左  "+(m!=null&&m.leftWatts!=null?m.leftWatts:"—")+" W");rightValue.setText("右  "+(m!=null&&m.rightWatts!=null?m.rightWatts:"—")+" W");
        Integer percent=m==null?null:m.leftPercent();balanceBar.setProgress(percent==null?0:percent);balanceBar.setAlpha(percent==null?.3f:1f);
        balanceValue.setText(percent!=null?"左 "+percent+"%  /  右 "+(100-percent)+"% · 参考比例":m==null?"等待有效数据":m.watts<10?"总功率低于 10W，暂不计算比例":"左右字段缺失或与总功率不一致");
        angleValue.setText("曲柄角度（参考）   "+(m!=null&&m.angle!=null?m.angle+"°":"—"));
        statusValue.setText("传感器状态   "+(!ProbeState.inputLinked?"—":ProbeState.sensorStatus!=0?"状态码 "+ProbeState.sensorStatus:fresh?"正常":"等待最新数据"));
        infoValue.setText("制造商   "+known(ProbeState.manufacturer)+"\n型号   "+known(ProbeState.model)+"\n固件   "+known(ProbeState.firmware)+"\n硬件   "+known(ProbeState.hardware));

        deviceSection.setVisibility(active&&!ProbeState.inputLinked?View.VISIBLE:View.GONE);
        rescan.setEnabled(!ProbeState.scanning);rescan.setAlpha(ProbeState.scanning?.5f:1f);rescan.setText(ProbeState.scanning?"搜索中…":"重新搜索");
        deviceHint.setText(ProbeState.devices.isEmpty()?"保持曲柄唤醒，手机靠近车身。首次使用请在此选设备。":"优先选择标记为“喜德盛私有服务”的设备。");
        StringBuilder signature=new StringBuilder();for(ProbeState.Nearby n:ProbeState.devices)signature.append(n.address).append(n.name).append(n.xds);
        if(!deviceSignature.equals(signature.toString())){
            deviceSignature=signature.toString();devices.removeAllViews();
            for(ProbeState.Nearby n:ProbeState.devices){
                LinearLayout tile=card();tile.setPadding(dp(16),dp(14),dp(16),dp(14));TextView name=text(n.name+"  ›",15,n.xds?MINT:TEXT);add(tile,name);gap(tile,5);add(tile,text(n.xds?"喜德盛私有服务 · 点击连接":n.address+" · 点击检查兼容性",11,MUTED));tile.setContentDescription("连接 "+n.name);tile.setOnClickListener(v->send("select",n.address));tile.setClickable(true);add(devices,tile);gap(devices,8);
            }
        }
        if(diagnostics.getVisibility()==View.VISIBLE){stats.setText("收到 "+ProbeState.packets+" 包 · 转发回调 "+ProbeState.sent+" 次\n码表连接 "+ProbeState.headConnections+" · 功率订阅 "+ProbeState.headSubscriptions+"\n踏频转数由实际 rpm 积分换算。");logText.setText(ProbeState.logs()+"\n服务与特征清单\n"+ProbeState.inventory);}
    }
    private static String known(String value){return value==null||value.isEmpty()?"—":value;}
    private boolean hasNearby(){if(Build.VERSION.SDK_INT>=31)return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED;return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void startBridge(){
        ArrayList<String> needed=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=31){for(String p:new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE,Manifest.permission.BLUETOOTH_SCAN})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)needed.add(p);}
        else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!needed.isEmpty()){requestPermissions(needed.toArray(new String[0]),1);return;}send("start",null);
    }
    private void send(String action,String address){
        try{Intent i=new Intent(this,PowerService.class).setAction(action);if(address!=null)i.putExtra("address",address);startForegroundService(i);}
        catch(RuntimeException e){ProbeState.inputStatus="无法启动："+e.getMessage();ProbeState.log(ProbeState.inputStatus);render();}
    }
    public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==1){if(hasNearby())send("start",null);else{ProbeState.inputStatus="请允许附近设备权限，才能连接功率计和码表";render();}}}
    protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private final class PowerTrace extends View {
        private final ArrayList<Integer> values=new ArrayList<>();private final Paint paint=new Paint(3);private boolean fresh;
        PowerTrace(){super(MainActivity.this);setContentDescription("真实功率最近变化");}
        void point(int w){values.add(w);if(values.size()>50)values.remove(0);invalidate();}
        void reset(){values.clear();invalidate();}
        void setFresh(boolean value){if(fresh!=value){fresh=value;invalidate();}}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);int width=getWidth(),height=getHeight();paint.setColor(LINE);paint.setStrokeWidth(dp(1));for(int j=1;j<4;j++)canvas.drawLine(0,height*j/4f,width,height*j/4f,paint);if(values.isEmpty())return;
            float max=200;for(int w:values)max=Math.max(max,w);Path path=new Path();for(int i=0;i<values.size();i++){float x=width*(i/49f),y=height-dp(3)-(height-dp(6))*values.get(i)/max;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}paint.setColor(fresh?MINT:MUTED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        }
    }
}
