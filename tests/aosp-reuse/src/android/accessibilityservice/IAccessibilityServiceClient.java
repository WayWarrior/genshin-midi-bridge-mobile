package android.accessibilityservice;import android.os.RemoteException;public interface IAccessibilityServiceClient{void onPerformGestureResult(int seq,boolean success)throws RemoteException;}
