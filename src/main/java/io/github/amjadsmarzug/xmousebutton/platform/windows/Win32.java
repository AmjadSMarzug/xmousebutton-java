package io.github.amjadsmarzug.xmousebutton.platform.windows;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/** The handful of Win32 calls this backend needs beyond what jna-platform already wraps. */
final class Win32 {

    private Win32() {
    }

    /** Signature of a WH_MOUSE_LL hook procedure. */
    interface LowLevelMouseProc extends StdCallLibrary.StdCallCallback {
        Pointer callback(int nCode, Pointer wParam, Pointer lParam);
    }

    interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer SetWindowsHookExW(int idHook, LowLevelMouseProc lpfn, Pointer hMod, int dwThreadId);

        boolean UnhookWindowsHookEx(Pointer hhk);

        Pointer CallNextHookEx(Pointer hhk, int nCode, Pointer wParam, Pointer lParam);

        Pointer GetForegroundWindow();

        int GetWindowTextW(Pointer hWnd, char[] lpString, int nMaxCount);

        int GetWindowThreadProcessId(Pointer hWnd, IntByReference lpdwProcessId);

        void keybd_event(byte bVk, byte bScan, int dwFlags, Pointer dwExtraInfo);

        void mouse_event(int dwFlags, int dx, int dy, int dwData, Pointer dwExtraInfo);

        int SendInput(int nInputs, Pointer pInputs, int cbSize);

        boolean SystemParametersInfoW(int uiAction, int uiParam, Pointer pvParam, int fWinIni);
    }

    interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer GetModuleHandleW(Pointer lpModuleName);

        Pointer OpenProcess(int dwDesiredAccess, boolean bInheritHandle, int dwProcessId);

        boolean CloseHandle(Pointer hObject);

        boolean QueryFullProcessImageNameW(Pointer hProcess, int dwFlags, char[] lpExeName, IntByReference lpdwSize);
    }
}
