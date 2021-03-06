/*
 * Copyright 2013 ParanoidAndroid Project
 *
 * This file is part of Paranoid OTA.
 *
 * Paranoid OTA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Paranoid OTA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Paranoid OTA.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.paranoid.paranoidota;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import com.paranoid.paranoidota.helpers.SettingsHelper;
import com.paranoid.paranoidota.updater.Updater;
import com.paranoid.paranoidota.updater.Updater.PackageInfo;

public class Utils {

    public static final String FILES_INFO = "com.paranoid.paranoidota.Utils.FILES_INFO";
    public static final String CHECK_DOWNLOADS_FINISHED = "com.paranoid.paranoidota.Utils.CHECK_DOWNLOADS_FINISHED";
    public static final String CHECK_DOWNLOADS_ID = "com.paranoid.paranoidota.Utils.CHECK_DOWNLOADS_ID";
    public static final String MOD_VERSION = "ro.modversion";
    public static final String RO_PA = "ro.pa";
    public static final int ROM_ALARM_ID = 122303221;
    public static final int GAPPS_ALARM_ID = 122303222;

    public static PackageInfo[] sPackageInfosRom = new PackageInfo[0];
    public static PackageInfo[] sPackageInfosGapps = new PackageInfo[0];
    private static int sWeAreInAospa = -1;
    
	private static int mTheme;

	public final static int Holo = 0;	
	public final static int Holo_Light = 1 ;	
	public final static int Holo_Light_DAB = 2;    

    public static class NotificationInfo implements Serializable {

        public int mNotificationId;
        public PackageInfo[] mPackageInfosRom;
        public PackageInfo[] mPackageInfosGapps;
    }

    public static String getProp(String prop) {
        try {
            Process process = Runtime.getRuntime().exec("getprop " + prop);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                    process.getInputStream()));
            StringBuilder log = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                log.append(line);
            }
            return log.toString();
        } catch (IOException e) {
            // Runtime error
        }
        return null;
    }

    public static String getReadableVersion(String version) {
        try {
            String number = version.substring(version.indexOf("-") + 1, version.lastIndexOf("-"));
            String date = version.substring(version.lastIndexOf("-") + 1,
                    version.endsWith(".zip") ? version.lastIndexOf(".") : version.length());
    
            SimpleDateFormat curFormater = new SimpleDateFormat("yyyyMMdd");
            Date dateObj = null;
            try {
                dateObj = curFormater.parse(date);
            } catch (ParseException e) {
                // ignore
            }
            SimpleDateFormat postFormater = new SimpleDateFormat("MMMM dd, yyyy");
    
            if (dateObj == null) {
                return number;
            }
            String newDateStr = postFormater.format(dateObj);
    
            StringBuilder b = new StringBuilder(newDateStr);
            int i = 0;
            do {
                b.replace(i, i + 1, b.substring(i, i + 1).toUpperCase());
                i = b.indexOf(" ", i) + 1;
            } while (i > 0 && i < b.length());
            return number + " - " + b.toString();
        } catch (Exception ex) {
            // unknown version format
            return version;
        }
    }

    public static long parseRomVersion(String version) {
        String stripped = version.replaceAll(".1-RC1-", "-");
        stripped = stripped.replaceAll("-RC2-", "-");
        stripped = stripped.replaceAll("\\D+", "");
        return "".equals(stripped) ? 0L : Long.parseLong(stripped);
    }

    public static String translateDeviceName(Context context, String device) {
        Properties dictionary = IOUtils.getDictionary(context);
        String translate = dictionary.getProperty(device);
        if (translate == null) {
            translate = device;
            String[] remove = dictionary.getProperty("@remove").split(",");
            for (int i = 0; i < remove.length; i++) {
                if (translate.indexOf(remove[i]) >= 0) {
                    translate = translate.replace(remove[i], "");
                    break;
                }
            }
        }
        return translate;
    }

    public static String getDateAndTime() {
        return new SimpleDateFormat("yyyy-MM-dd.HH.mm.ss").format(new Date(System
                .currentTimeMillis()));
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public static void setAlarm(Context context, boolean trigger, boolean isRom) {

        SettingsHelper helper = new SettingsHelper(context);
        setAlarm(context, helper.getCheckTime(), trigger, isRom);
    }

    public static void setAlarm(Context context, long time, boolean trigger, boolean isRom) {

        Intent i = new Intent(context, NotificationAlarm.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pi = PendingIntent.getBroadcast(context,
                isRom ? ROM_ALARM_ID : GAPPS_ALARM_ID, i,
                PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        if (time > 0) {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger ? 0 : time, time, pi);
        }
    }

    public static boolean alarmExists(Context context, boolean isRom) {
        return (PendingIntent.getBroadcast(context, isRom ? ROM_ALARM_ID
                : GAPPS_ALARM_ID, new Intent(context, NotificationAlarm.class),
                PendingIntent.FLAG_NO_CREATE) != null);
    }

    public static void showToastOnUiThread(final Context context, final int resourceId) {
        ((Activity) context).runOnUiThread(new Runnable() {

            public void run() {
                Toast.makeText(context, resourceId, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static void showToastOnUiThread(final Context context, final String string) {
        ((Activity) context).runOnUiThread(new Runnable() {

            public void run() {
                Toast.makeText(context, string, Toast.LENGTH_LONG).show();
            }
        });
    }
    
	public static void changeToTheme(Activity activity, int theme) {
		
		mTheme = theme;
		
		activity.finish();
		
		activity.startActivity(new Intent(activity, activity.getClass()));
		
	}
	
	public static void onActivityCreateSetTheme(Activity activity) {
		
		switch (mTheme) {
		
		case Holo:
			activity.setTheme(R.style.Holo);
			break;			
		
		case Holo_Light:
			activity.setTheme(R.style.Holo_Light);
			break;		
			
		case Holo_Light_DAB:
			activity.setTheme(R.style.Holo_Light_DAB);
			break;
		}
	}

    public static void showNotification(Context context, Updater.PackageInfo[] infosRom,
            Updater.PackageInfo[] infosGapps) {
        Resources resources = context.getResources();

        if (infosRom != null) {
            sPackageInfosRom = infosRom;
        } else {
            infosRom = sPackageInfosRom;
        }
        if (infosGapps != null) {
            sPackageInfosGapps = infosGapps;
        } else {
            infosGapps = sPackageInfosGapps;
        }

        int contentTitleResourceId = -1;
        if (infosRom.length > 0 && infosGapps.length > 0) {
            contentTitleResourceId = !Utils.weAreInAospa() ? R.string.update_all_to_aospa
                    : R.string.new_all_found_title;
        } else if (infosRom.length == 0) {
            contentTitleResourceId = !Utils.weAreInAospa() ? R.string.update_gapps_to_aospa
                    : R.string.new_gapps_found_title;
        } else {
            contentTitleResourceId = !Utils.weAreInAospa() ? R.string.update_rom_to_aospa
                    : R.string.new_rom_found_title;
        }

        Intent intent = new Intent(context, MainActivity.class);
        NotificationInfo fileInfo = new NotificationInfo();
        fileInfo.mNotificationId = Updater.NOTIFICATION_ID;
        fileInfo.mPackageInfosRom = infosRom;
        fileInfo.mPackageInfosGapps = infosGapps;
        intent.putExtra(FILES_INFO, fileInfo);
        PendingIntent pIntent = PendingIntent.getActivity(context, Updater.NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setContentTitle(resources.getString(contentTitleResourceId))
                .setSmallIcon(R.drawable.ic_launcher).setContentIntent(pIntent);

        String contextText = "";
        if (infosRom.length + infosGapps.length == 1) {
            String filename = infosRom.length == 1 ? infosRom[0].getFilename() : infosGapps[0]
                    .getFilename();
            contextText = resources.getString(R.string.new_package_name, new Object[] { filename });
        } else {
            contextText = resources.getString(R.string.new_packages, new Object[] { infosRom.length
                    + infosGapps.length });
        }
        builder.setContentText(contextText);

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle(context.getResources().getString(contentTitleResourceId));
        if (infosRom.length + infosGapps.length > 1) {
            inboxStyle.addLine(contextText);
        }
        for (int i = 0; i < infosRom.length; i++) {
            inboxStyle.addLine(infosRom[i].getFilename());
        }
        for (int i = 0; i < infosGapps.length; i++) {
            inboxStyle.addLine(infosGapps[i].getFilename());
        }
        builder.setStyle(inboxStyle);

        Notification notif = builder.build();

        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Service.NOTIFICATION_SERVICE);

        notif.flags |= Notification.FLAG_AUTO_CANCEL;

        notificationManager.notify(Updater.NOTIFICATION_ID, notif);
    }

    public static boolean isSystemApp(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        android.content.pm.PackageInfo info = pm.getPackageInfo("com.paranoid.paranoidota",
                PackageManager.GET_ACTIVITIES);
        ApplicationInfo aInfo = info.applicationInfo;
        String path = aInfo.sourceDir.substring(0, aInfo.sourceDir.lastIndexOf("/"));
        boolean isSystemApp = path.contains("system/app");
        return isSystemApp;
    }

    public static boolean weAreInAospa() {
        if (sWeAreInAospa == -1) {
            String prop = getProp(RO_PA);
            sWeAreInAospa = "true".equals(prop) ? 1 : 0;
        }
        return sWeAreInAospa == 1;
    }

    public static String su(String[] commands) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            for (int i = 0; i < commands.length; i++) {
                os.writeBytes(commands[i] + "\n");
            }
            os.writeBytes("sync\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
            return getStreamLines(p.getInputStream());
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static String getStreamLines(final InputStream is) {
        String out = null;
        StringBuffer buffer = null;
        final DataInputStream dis = new DataInputStream(is);

        try {
            if (dis.available() > 0) {
                buffer = new StringBuffer(dis.readLine());
                while (dis.available() > 0) {
                    buffer.append("\n").append(dis.readLine());
                }
            }
            dis.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (buffer != null) {
            out = buffer.toString();
        }
        return out;
    }
}
