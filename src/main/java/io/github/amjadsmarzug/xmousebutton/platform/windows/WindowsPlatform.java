package io.github.amjadsmarzug.xmousebutton.platform.windows;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import io.github.amjadsmarzug.xmousebutton.model.ButtonEvent;
import io.github.amjadsmarzug.xmousebutton.model.KeyCombo;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import io.github.amjadsmarzug.xmousebutton.model.WindowInfo;
import io.github.amjadsmarzug.xmousebutton.platform.Platform;

import com.sun.jna.Memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Windows backend. Uses a WH_MOUSE_LL hook on a dedicated message-loop thread and injects input
 * with keybd_event / mouse_event. Injected input is tagged with {@link #INJECTED_MARKER} so the
 * hook can recognise and ignore its own output.
 */
public final class WindowsPlatform implements Platform {

    static final long INJECTED_MARKER = 0x584D4258L; // "XMBX"

    private static final int WH_MOUSE_LL = 14;
    private static final int WM_LBUTTONDOWN = 0x0201;
    private static final int WM_LBUTTONUP = 0x0202;
    private static final int WM_RBUTTONDOWN = 0x0204;
    private static final int WM_RBUTTONUP = 0x0205;
    private static final int WM_MBUTTONDOWN = 0x0207;
    private static final int WM_MBUTTONUP = 0x0208;
    private static final int WM_MOUSEWHEEL = 0x020A;
    private static final int WM_XBUTTONDOWN = 0x020B;
    private static final int WM_XBUTTONUP = 0x020C;
    private static final int WM_MOUSEHWHEEL = 0x020E;

    /** Offsets inside MSLLHOOKSTRUCT. dwExtraInfo sits after pt, mouseData, flags and time. */
    private static final long OFFSET_MOUSE_DATA = 8;
    private static final long OFFSET_EXTRA_INFO = Native.POINTER_SIZE == 8 ? 24 : 20;

    private static final int MOUSEEVENTF_LEFTDOWN = 0x0002;
    private static final int MOUSEEVENTF_LEFTUP = 0x0004;
    private static final int MOUSEEVENTF_RIGHTDOWN = 0x0008;
    private static final int MOUSEEVENTF_RIGHTUP = 0x0010;
    private static final int MOUSEEVENTF_MIDDLEDOWN = 0x0020;
    private static final int MOUSEEVENTF_MIDDLEUP = 0x0040;
    private static final int MOUSEEVENTF_XDOWN = 0x0080;
    private static final int MOUSEEVENTF_XUP = 0x0100;
    private static final int MOUSEEVENTF_WHEEL = 0x0800;
    private static final int MOUSEEVENTF_HWHEEL = 0x1000;
    private static final int WHEEL_DELTA = 120;

    private static final int KEYEVENTF_EXTENDEDKEY = 0x0001;
    private static final int KEYEVENTF_KEYUP = 0x0002;
    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;

    private static final int INPUT_KEYBOARD = 1;
    private static final int KEYEVENTF_UNICODE = 0x0004;
    private static final int SPI_GETMOUSESPEED = 0x0070;
    private static final int SPI_SETMOUSESPEED = 0x0071;

    private volatile Predicate<ButtonEvent> handler;
    /** Pointer speed before this process changed it, or null if unchanged. Guarded by this. */
    private Integer savedPointerSpeed;
    private Win32.LowLevelMouseProc callback;
    private Pointer hook;

    @Override
    public String name() {
        return "windows";
    }

    @Override
    public boolean supportsMouseHook() {
        return true;
    }

    @Override
    public void startMouseHook(Predicate<ButtonEvent> handler) throws IOException {
        recoverPointerSpeed();
        this.handler = handler;
        CompletableFuture<Void> ready = new CompletableFuture<>();
        Thread thread = new Thread(() -> runMessageLoop(ready), "xmousebutton-hook");
        thread.setDaemon(true);
        thread.start();
        try {
            ready.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new IOException("mouse hook failed: " + e.getCause().getMessage(), e.getCause());
        } catch (TimeoutException e) {
            throw new IOException("mouse hook did not start in time", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while starting mouse hook", e);
        }
    }

    private void runMessageLoop(CompletableFuture<Void> ready) {
        try {
            Pointer module = Win32.Kernel32.INSTANCE.GetModuleHandleW(null);
            callback = this::onLowLevelMouse;
            hook = Win32.User32.INSTANCE.SetWindowsHookExW(WH_MOUSE_LL, callback, module, 0);
            if (hook == null) {
                throw new IOException("SetWindowsHookEx failed with error " + Native.getLastError());
            }
            ready.complete(null);

            WinUser.MSG msg = new WinUser.MSG();
            while (User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }
        } catch (Throwable t) {
            ready.completeExceptionally(t);
        }
    }

    private Pointer onLowLevelMouse(int nCode, Pointer wParam, Pointer lParam) {
        if (nCode >= 0 && lParam != null && !isOwnInjection(lParam)) {
            ButtonEvent event = decode((int) Pointer.nativeValue(wParam), lParam);
            if (event != null) {
                try {
                    if (handler.test(event)) {
                        return Pointer.createConstant(1);
                    }
                } catch (RuntimeException e) {
                    // Never let a mapping bug kill the hook; pass the input through instead.
                    System.err.println("mapping error: " + e);
                }
            }
        }
        return Win32.User32.INSTANCE.CallNextHookEx(hook, nCode, wParam, lParam);
    }

    private static ButtonEvent decode(int message, Pointer lParam) {
        int mouseData = lParam.getInt(OFFSET_MOUSE_DATA);
        return switch (message) {
            case WM_LBUTTONDOWN -> ButtonEvent.press(MouseButton.LEFT);
            case WM_LBUTTONUP -> ButtonEvent.release(MouseButton.LEFT);
            case WM_RBUTTONDOWN -> ButtonEvent.press(MouseButton.RIGHT);
            case WM_RBUTTONUP -> ButtonEvent.release(MouseButton.RIGHT);
            case WM_MBUTTONDOWN -> ButtonEvent.press(MouseButton.MIDDLE);
            case WM_MBUTTONUP -> ButtonEvent.release(MouseButton.MIDDLE);
            case WM_XBUTTONDOWN, WM_XBUTTONUP -> {
                int which = (mouseData >>> 16) & 0xFFFF;
                MouseButton button = which == 1 ? MouseButton.X1 : which == 2 ? MouseButton.X2 : null;
                if (button == null) {
                    yield null;
                }
                yield new ButtonEvent(button, message == WM_XBUTTONDOWN);
            }
            case WM_MOUSEWHEEL -> {
                short delta = (short) ((mouseData >>> 16) & 0xFFFF);
                yield delta > 0 ? ButtonEvent.press(MouseButton.WHEEL_UP)
                        : delta < 0 ? ButtonEvent.press(MouseButton.WHEEL_DOWN) : null;
            }
            case WM_MOUSEHWHEEL -> {
                short delta = (short) ((mouseData >>> 16) & 0xFFFF);
                yield delta > 0 ? ButtonEvent.press(MouseButton.WHEEL_RIGHT)
                        : delta < 0 ? ButtonEvent.press(MouseButton.WHEEL_LEFT) : null;
            }
            default -> null;
        };
    }

    private static boolean isOwnInjection(Pointer lParam) {
        Pointer extra = lParam.getPointer(OFFSET_EXTRA_INFO);
        return extra != null && Pointer.nativeValue(extra) == INJECTED_MARKER;
    }

    @Override
    public void stopMouseHook() {
        if (hook != null) {
            Win32.User32.INSTANCE.UnhookWindowsHookEx(hook);
            hook = null;
        }
        setPointerSpeed(null);
    }

    @Override
    public synchronized void setPointerSpeed(Integer speed) {
        if (speed == null) {
            if (savedPointerSpeed != null) {
                writePointerSpeed(savedPointerSpeed);
                savedPointerSpeed = null;
                deleteBackup();
            }
            return;
        }
        if (speed < 1 || speed > 20) {
            throw new IllegalArgumentException("pointer speed must be 1 to 20");
        }
        if (savedPointerSpeed == null) {
            savedPointerSpeed = readPointerSpeed();
            writeBackup(savedPointerSpeed);
        }
        writePointerSpeed(speed);
    }

    private static int readPointerSpeed() {
        Memory value = new Memory(4);
        if (!Win32.User32.INSTANCE.SystemParametersInfoW(SPI_GETMOUSESPEED, 0, value, 0)) {
            throw new IllegalStateException("cannot read pointer speed, error " + Native.getLastError());
        }
        return value.getInt(0);
    }

    private static void writePointerSpeed(int speed) {
        if (!Win32.User32.INSTANCE.SystemParametersInfoW(SPI_SETMOUSESPEED, 0, Pointer.createConstant(speed), 0)) {
            throw new IllegalStateException("cannot set pointer speed, error " + Native.getLastError());
        }
    }

    /**
     * The speed is changed for the live session only. If the app crashed, the backup file still
     * holds the original value, so put it back before doing anything else.
     */
    private static void recoverPointerSpeed() {
        Path backup = backupPath();
        if (!Files.exists(backup)) {
            return;
        }
        try {
            int original = Integer.parseInt(Files.readString(backup, StandardCharsets.UTF_8).trim());
            if (original >= 1 && original <= 20) {
                writePointerSpeed(original);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("could not restore pointer speed from " + backup + ": " + e.getMessage());
            return;
        }
        deleteBackup();
    }

    private static void writeBackup(int speed) {
        try {
            Path backup = backupPath();
            Files.createDirectories(backup.getParent());
            Files.writeString(backup, Integer.toString(speed), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("warning: could not write pointer speed backup: " + e.getMessage());
        }
    }

    private static void deleteBackup() {
        try {
            Files.deleteIfExists(backupPath());
        } catch (IOException e) {
            System.err.println("warning: could not remove pointer speed backup: " + e.getMessage());
        }
    }

    private static Path backupPath() {
        return Paths.get(System.getProperty("user.home"), ".xmousebutton", "pointer-speed.bak");
    }

    @Override
    public void typeText(String text) {
        for (char c : text.toCharArray()) {
            sendUnicode(c, true);
            sendUnicode(c, false);
        }
    }

    /** Sends one UTF-16 code unit with SendInput. Layout depends on INPUT's native size. */
    private static void sendUnicode(char c, boolean down) {
        boolean is64 = Native.POINTER_SIZE == 8;
        int inputSize = is64 ? 40 : 28;
        long union = is64 ? 8 : 4;          // offset of the KEYBDINPUT member
        long extraOffset = is64 ? union + 16 : union + 12;
        Memory input = new Memory(inputSize);
        input.clear();
        input.setInt(0, INPUT_KEYBOARD);
        input.setShort(union + 2, (short) c); // wScan carries the character
        input.setInt(union + 4, KEYEVENTF_UNICODE | (down ? 0 : KEYEVENTF_KEYUP));
        input.setPointer(extraOffset, Pointer.createConstant(INJECTED_MARKER));
        Win32.User32.INSTANCE.SendInput(1, input, inputSize);
    }

    @Override
    public WindowInfo foregroundWindow() {
        Pointer hwnd = Win32.User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) {
            return WindowInfo.UNKNOWN;
        }
        char[] titleBuffer = new char[512];
        int length = Win32.User32.INSTANCE.GetWindowTextW(hwnd, titleBuffer, titleBuffer.length);
        String title = length > 0 ? new String(titleBuffer, 0, length) : "";

        IntByReference pid = new IntByReference();
        Win32.User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
        return new WindowInfo(processName(pid.getValue()), title);
    }

    private static String processName(int pid) {
        if (pid == 0) {
            return "";
        }
        Pointer process = Win32.Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
        if (process == null) {
            return "";
        }
        try {
            char[] buffer = new char[1024];
            IntByReference size = new IntByReference(buffer.length);
            if (!Win32.Kernel32.INSTANCE.QueryFullProcessImageNameW(process, 0, buffer, size)) {
                return "";
            }
            String path = new String(buffer, 0, size.getValue());
            return path.substring(path.lastIndexOf('\\') + 1);
        } finally {
            Win32.Kernel32.INSTANCE.CloseHandle(process);
        }
    }

    @Override
    public void tapKeys(KeyCombo combo) {
        for (int mod : combo.modifiers()) {
            keyEvent(mod, true);
        }
        keyEvent(combo.key(), true);
        keyEvent(combo.key(), false);
        for (int i = combo.modifiers().size() - 1; i >= 0; i--) {
            keyEvent(combo.modifiers().get(i), false);
        }
    }

    private static void keyEvent(int vk, boolean down) {
        int flags = (down ? 0 : KEYEVENTF_KEYUP) | (isExtendedKey(vk) ? KEYEVENTF_EXTENDEDKEY : 0);
        Win32.User32.INSTANCE.keybd_event((byte) vk, (byte) 0, flags, marker());
    }

    /** Keys that need the extended-key flag to be interpreted correctly by Windows. */
    private static boolean isExtendedKey(int vk) {
        return (vk >= 0x21 && vk <= 0x28) || vk == 0x2D || vk == 0x2E || vk == 0x5B || vk == 0x5C;
    }

    @Override
    public void sendButton(MouseButton button, boolean down) {
        int flags;
        int data = 0;
        switch (button) {
            case LEFT -> flags = down ? MOUSEEVENTF_LEFTDOWN : MOUSEEVENTF_LEFTUP;
            case RIGHT -> flags = down ? MOUSEEVENTF_RIGHTDOWN : MOUSEEVENTF_RIGHTUP;
            case MIDDLE -> flags = down ? MOUSEEVENTF_MIDDLEDOWN : MOUSEEVENTF_MIDDLEUP;
            case X1 -> {
                flags = down ? MOUSEEVENTF_XDOWN : MOUSEEVENTF_XUP;
                data = 1;
            }
            case X2 -> {
                flags = down ? MOUSEEVENTF_XDOWN : MOUSEEVENTF_XUP;
                data = 2;
            }
            default -> throw new IllegalArgumentException("not a physical button: " + button);
        }
        Win32.User32.INSTANCE.mouse_event(flags, 0, 0, data, marker());
    }

    @Override
    public void sendWheel(MouseButton direction, int notches) {
        for (int i = 0; i < notches; i++) {
            switch (direction) {
                case WHEEL_UP -> Win32.User32.INSTANCE.mouse_event(MOUSEEVENTF_WHEEL, 0, 0, WHEEL_DELTA, marker());
                case WHEEL_DOWN -> Win32.User32.INSTANCE.mouse_event(MOUSEEVENTF_WHEEL, 0, 0, -WHEEL_DELTA, marker());
                case WHEEL_RIGHT -> Win32.User32.INSTANCE.mouse_event(MOUSEEVENTF_HWHEEL, 0, 0, WHEEL_DELTA, marker());
                case WHEEL_LEFT -> Win32.User32.INSTANCE.mouse_event(MOUSEEVENTF_HWHEEL, 0, 0, -WHEEL_DELTA, marker());
                default -> throw new IllegalArgumentException("not a wheel direction: " + direction);
            }
        }
    }

    @Override
    public void launch(List<String> command) throws IOException {
        new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static Pointer marker() {
        return Pointer.createConstant(INJECTED_MARKER);
    }
}
