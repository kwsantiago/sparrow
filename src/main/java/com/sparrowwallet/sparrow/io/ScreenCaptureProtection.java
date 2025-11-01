package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.OsType;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Provides cross-platform screen capture prevention for JavaFX windows.
 * Similar to Electron's win.setContentProtection() API.
 *
 * Platform Support:
 * - Windows 10 (build 19041+): High effectiveness via SetWindowDisplayAffinity
 * - macOS: Moderate effectiveness via NSWindow sharingType (deprecated but functional)
 * - Linux: Not supported (compositor-dependent)
 */
public class ScreenCaptureProtection {
    private static final Logger log = LoggerFactory.getLogger(ScreenCaptureProtection.class);

    /**
     * Apply screen capture protection to a JavaFX Stage.
     *
     * @param stage The Stage to protect
     * @param prevent true to prevent screen capture, false to allow it
     */
    public static void setProtection(Stage stage, boolean prevent) {
        if(stage == null) {
            return;
        }

        try {
            OsType osType = OsType.getCurrent();
            if(osType == OsType.WINDOWS) {
                applyWindowsProtection(stage, prevent);
            } else if(osType == OsType.MACOS) {
                applyMacOsProtection(stage, prevent);
            } else {
                log.debug("Screen capture protection not supported on " + osType);
            }
        } catch(Exception e) {
            log.error("Failed to apply screen capture protection", e);
        }
    }

    /**
     * Windows implementation using SetWindowDisplayAffinity.
     * Requires Windows 10 version 2004 (build 19041) or later for WDA_EXCLUDEFROMCAPTURE.
     */
    private static void applyWindowsProtection(Stage stage, boolean prevent) {
        try {
            // Get the native window handle (HWND) via JavaFX Glass API
            long hwnd = getWindowsHandle(stage);
            if(hwnd == 0) {
                log.warn("Could not get native window handle for Windows protection");
                return;
            }

            // Call SetWindowDisplayAffinity via JNA
            int affinity = prevent ? User32.WDA_EXCLUDEFROMCAPTURE : User32.WDA_NONE;
            boolean success = User32.INSTANCE.SetWindowDisplayAffinity(new Pointer(hwnd), affinity);

            if(success) {
                log.info("Windows screen capture protection " + (prevent ? "enabled" : "disabled"));
            } else {
                log.warn("SetWindowDisplayAffinity failed (Windows 10 build 19041+ required)");
            }
        } catch(Exception e) {
            log.error("Failed to apply Windows screen capture protection", e);
        }
    }

    /**
     * macOS implementation using NSWindow sharingType property.
     * Note: NSWindowSharingNone is deprecated but still functional on most macOS versions.
     */
    private static void applyMacOsProtection(Stage stage, boolean prevent) {
        try {
            // Get the native NSWindow pointer via JavaFX Glass API
            long nsWindowPtr = getMacOsHandle(stage);
            if(nsWindowPtr == 0) {
                log.warn("Could not get NSWindow pointer for macOS protection");
                return;
            }

            Pointer nsWindow = new Pointer(nsWindowPtr);

            // Set sharingType property via Objective-C runtime
            // NSWindowSharingNone = 0, NSWindowSharingReadOnly = 1
            long sharingType = prevent ? 0L : 1L; // 0 = NSWindowSharingNone

            // Call [nsWindow setSharingType:sharingType]
            Foundation.INSTANCE.objc_msgSend(nsWindow,
                Foundation.INSTANCE.sel_getUid("setSharingType:"),
                sharingType);

            log.info("macOS screen capture protection " + (prevent ? "enabled" : "disabled"));
        } catch(Exception e) {
            log.error("Failed to apply macOS screen capture protection", e);
        }
    }

    /**
     * Get Windows HWND from JavaFX Stage using reflection.
     */
    private static long getWindowsHandle(Stage stage) throws Exception {
        // Access: stage.impl_getPeer() -> com.sun.glass.ui.Window -> getNativeHandle()
        Method getPeerMethod = stage.getClass().getMethod("impl_getPeer");
        getPeerMethod.setAccessible(true);
        Object peer = getPeerMethod.invoke(stage);

        if(peer == null) {
            return 0;
        }

        Method getNativeHandleMethod = peer.getClass().getMethod("getNativeHandle");
        getNativeHandleMethod.setAccessible(true);
        return (long) getNativeHandleMethod.invoke(peer);
    }

    /**
     * Get macOS NSWindow pointer from JavaFX Stage using reflection.
     */
    private static long getMacOsHandle(Stage stage) throws Exception {
        // Similar to Windows, get the native handle via Glass UI
        Method getPeerMethod = stage.getClass().getMethod("impl_getPeer");
        getPeerMethod.setAccessible(true);
        Object peer = getPeerMethod.invoke(stage);

        if(peer == null) {
            return 0;
        }

        Method getNativeHandleMethod = peer.getClass().getMethod("getNativeHandle");
        getNativeHandleMethod.setAccessible(true);
        return (long) getNativeHandleMethod.invoke(peer);
    }

    // JNA interface for Windows User32.dll
    private interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);

        int WDA_NONE = 0x00000000;
        int WDA_MONITOR = 0x00000001;
        int WDA_EXCLUDEFROMCAPTURE = 0x00000011; // Windows 10 build 19041+

        boolean SetWindowDisplayAffinity(Pointer hWnd, int dwAffinity);
    }

    // JNA interface for macOS Objective-C runtime
    private interface Foundation extends Library {
        Foundation INSTANCE = Native.load("Foundation", Foundation.class);

        Pointer sel_getUid(String name);
        Pointer objc_msgSend(Pointer receiver, Pointer selector, long arg);
    }
}
