package com.wurmonline.client;

import java.util.concurrent.atomic.AtomicReference;

/** Headless replacement for the desktop crash dialog; preserve errors and fail the attempt. */
public final class ErrorReporterPanel {
    private static final AtomicReference<Throwable> first = new AtomicReference<>();
    private ErrorReporterPanel() {}
    public static String androidCompatibilityVersion() { return "headless-errors-v1"; }
    public static Throwable androidFailure() { return first.get(); }
    private static void report(String kind, Throwable cause, String message) {
        Throwable failure = cause != null ? cause : new IllegalStateException(message == null ? kind : message);
        first.compareAndSet(null, failure);
        String detail = String.valueOf(message).replace('\n',' ').replace('\r',' ');
        System.out.println("[client] CLIENT_REPORTED_FAILURE kind="+kind+" message="+detail.substring(0,Math.min(220,detail.length()))+" desktopDialog=false");
        failure.printStackTrace(System.out);
    }
    public static void crashed(Throwable cause, String message) { report("crash",cause,message); }
    public static void failed(Throwable cause) { report("failure",cause,null); }
    public static void wrongVersion(Throwable cause) { report("version",cause,null); }
    public static void disconnected(Throwable cause) { report("disconnected",cause,null); }
    public static void connectDenied(String message) { report("login-denied",null,message); }
}
