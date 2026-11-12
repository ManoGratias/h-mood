package com.hmood.player;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Touches multimédia du clavier (⏯ ⏭ ⏮ ⏹) prises en compte même quand H MOOD est en arrière-plan,
 * via l'API Windows RegisterHotKey. Désactivable dans les Paramètres (les autres lecteurs, comme le navigateur,
 * ne reçoivent plus ces touches tant que H MOOD les utilise). Sans effet hors Windows.
 */
public final class GlobalMediaKeys {

    private static final int VK_MEDIA_NEXT_TRACK = 0xB0;
    private static final int VK_MEDIA_PREV_TRACK = 0xB1;
    private static final int VK_MEDIA_STOP = 0xB2;
    private static final int VK_MEDIA_PLAY_PAUSE = 0xB3;
    private static final int MOD_NOREPEAT = 0x4000;

    private final Map<Integer, Runnable> actions;
    private Thread thread;
    private volatile int threadId;
    private volatile List<String> failures = List.of();

    public GlobalMediaKeys(Runnable playPause, Runnable next, Runnable previous, Runnable stop) {
        this.actions = Map.of(VK_MEDIA_PLAY_PAUSE, playPause, VK_MEDIA_NEXT_TRACK, next,
                VK_MEDIA_PREV_TRACK, previous, VK_MEDIA_STOP, stop);
    }

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public synchronized boolean isRunning() {
        return thread != null;
    }

    /** Touches qu'un autre programme utilise déjà (non captées par H MOOD). */
    public List<String> failures() {
        return failures;
    }

    /** Démarre l'écoute ; renvoie faux si les touches ne peuvent pas être captées. */
    public synchronized boolean start() {
        if (!supported() || thread != null) return thread != null;
        CountDownLatch ready = new CountDownLatch(1);
        thread = new Thread(() -> loop(ready), "hmood-media-keys");
        thread.setDaemon(true);
        thread.start();
        try {
            ready.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return failures.size() < actions.size();
    }

    public synchronized void stop() {
        if (thread == null) return;
        if (threadId != 0) {
            User32.INSTANCE.PostThreadMessage(threadId, WinUser.WM_QUIT, new WinDef.WPARAM(0), new WinDef.LPARAM(0));
        }
        thread = null;
    }

    private void loop(CountDownLatch ready) {
        List<Integer> registered = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        try {
            threadId = Kernel32.INSTANCE.GetCurrentThreadId();
            for (int vk : actions.keySet()) {
                if (User32.INSTANCE.RegisterHotKey(null, vk, MOD_NOREPEAT, vk)) registered.add(vk);
                else failed.add(name(vk));
            }
            failures = List.copyOf(failed);
        } catch (Throwable t) { // bibliothèque native indisponible : les raccourcis internes restent actifs
            failures = List.of("touches multimédia (" + t.getClass().getSimpleName() + ")");
            ready.countDown();
            return;
        }
        ready.countDown();
        WinUser.MSG msg = new WinUser.MSG();
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
            if (msg.message == WinUser.WM_HOTKEY) {
                Runnable action = actions.get(msg.wParam.intValue());
                if (action != null) Platform.runLater(action);
            }
        }
        for (int vk : registered) User32.INSTANCE.UnregisterHotKey(null, vk);
        threadId = 0;
    }

    private static String name(int vk) {
        return switch (vk) {
            case VK_MEDIA_PLAY_PAUSE -> "Lecture/Pause";
            case VK_MEDIA_NEXT_TRACK -> "Suivant";
            case VK_MEDIA_PREV_TRACK -> "Précédent";
            default -> "Stop";
        };
    }
}
