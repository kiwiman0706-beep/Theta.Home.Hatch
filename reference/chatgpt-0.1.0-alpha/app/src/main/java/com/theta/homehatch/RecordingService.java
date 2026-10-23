package com.theta.homehatch;

import android.app.*;
import android.content.*;
import android.media.MediaRecorder;
import android.os.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecordingService extends Service {
    private MediaRecorder recorder;
    private static volatile boolean running;
    private File output;
    public static boolean active(Context c){return running;}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&"stop".equals(intent.getAction())){finish();return START_NOT_STICKY;}
        if(recorder!=null)return START_NOT_STICKY;
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("recording","録音",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RecordingService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        Notification note=new Notification.Builder(this,"recording").setSmallIcon(com.theta.homehatch.R.drawable.ic_hatch).setContentTitle("録音中").setContentText("停止するとRecordingsに保存します").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止",stop).build()).build();
        startForeground(77,note);
        try {
            File folder=new File(CaptureProvider.root(this),"Recordings");folder.mkdirs();
            output=new File(folder,new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date())+".m4a");
            recorder=new MediaRecorder();recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioEncodingBitRate(128000);recorder.setAudioSamplingRate(44100);recorder.setOutputFile(output.getAbsolutePath());recorder.prepare();recorder.start();running=true;
            getSharedPreferences("hatch",0).edit().putBoolean("recording",true).putLong("recStart",System.currentTimeMillis()).apply();
        } catch(Exception e){if(recorder!=null){recorder.release();recorder=null;}if(output!=null&&output.length()==0)output.delete();finish();}
        return START_NOT_STICKY;
    }
    private void finish(){
        running=false;
        if(recorder!=null){try{recorder.stop();}catch(RuntimeException e){if(output!=null)output.delete();}finally{recorder.release();recorder=null;}}
        getSharedPreferences("hatch",0).edit().putBoolean("recording",false).apply();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public void onDestroy(){running=false;if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();recorder=null;}getSharedPreferences("hatch",0).edit().putBoolean("recording",false).apply();super.onDestroy();}
}
