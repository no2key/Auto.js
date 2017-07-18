package com.stardust.view.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.InputDevice;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import com.stardust.util.ScreenMetrics;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Stardust on 2017/5/2.
 */

public class AccessibilityService extends android.accessibilityservice.AccessibilityService {


    private static final String TAG = "AccessibilityService";

    private static final SortedMap<Integer, AccessibilityDelegate> mDelegates = new TreeMap<>();
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final Condition ENABLED = LOCK.newCondition();
    private static AccessibilityService instance;
    private static boolean containsAllEventTypes = false;
    private static final Set<Integer> eventTypes = new HashSet<>();
    private volatile AccessibilityNodeInfo mRootInActiveWindow;
    private Timer mTimer;
    private Handler mHandler;

    public static void addDelegate(int uniquePriority, AccessibilityDelegate delegate) {
        mDelegates.put(uniquePriority, delegate);
        Set<Integer> set = delegate.getEventTypes();
        if (set == null)
            containsAllEventTypes = true;
        else
            eventTypes.addAll(set);
    }

    public static boolean isEnabled(Context context) {
        return AccessibilityServiceUtils.isAccessibilityServiceEnabled(context, AccessibilityService.class);
    }

    public static AccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onAccessibilityEvent(final AccessibilityEvent event) {
        Log.v(TAG, "onAccessibilityEvent: " + event);
        if (!containsAllEventTypes && !eventTypes.contains(event.getEventType()))
            return;
        for (Map.Entry<Integer, AccessibilityDelegate> entry : mDelegates.entrySet()) {
            AccessibilityDelegate delegate = entry.getValue();
            Set<Integer> types = delegate.getEventTypes();
            if (types != null && !delegate.getEventTypes().contains(event.getEventType()))
                continue;
            long start = System.currentTimeMillis();
            if (delegate.onAccessibilityEvent(AccessibilityService.this, event))
                break;
            Log.v(TAG, "millis: " + (System.currentTimeMillis() - start) + " delegate: " + entry.getValue().getClass().getName());
        }
    }


    @Override
    public void onInterrupt() {

    }

    @Override
    public AccessibilityNodeInfo getRootInActiveWindow() {
        return mRootInActiveWindow;
    }

    @Override
    public void onDestroy() {
        instance = null;
        mTimer.cancel();
        super.onDestroy();
    }


    @Override
    protected void onServiceConnected() {
        Log.v(TAG, "onServiceConnected: " + getServiceInfo().toString());
        instance = this;
        super.onServiceConnected();
        LOCK.lock();
        ENABLED.signalAll();
        LOCK.unlock();
        mHandler = new Handler();
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        AccessibilityNodeInfo root = superGetRootInActiveWindow();
                        if (root != null) {
                            mRootInActiveWindow = root;
                            Log.d(TAG, "getRootInActiveWindow: " + root);
                        }
                    }
                });

            }
        }, 0, 100);
        // FIXME: 2017/2/12 有时在无障碍中开启服务后这里不会调用服务也不会运行，安卓的BUG???
    }

    private AccessibilityNodeInfo superGetRootInActiveWindow() {
        try {
            return super.getRootInActiveWindow();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean disable() {
        if (instance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            instance.disableSelf();
            return true;
        }
        return false;
    }

    public static void waitForEnabled(long timeOut) {
        LOCK.lock();
        try {
            ENABLED.await(timeOut, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            LOCK.unlock();
        }
    }


}
