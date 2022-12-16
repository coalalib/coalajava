package com.ndmsystems.coala.helpers.logging;


public class SystemOutLogger implements ILogger {
    private final String tagPrefix;

    public SystemOutLogger(String tagPrefix) {
        this.tagPrefix = tagPrefix;
    }

    public void v(String message) {
        System.out.println(getTag("V") + " " + message);
    }

    public void d(String message) {
        System.out.println(getTag("D") + " " + message);
    }

    public void i(String message) {
        System.out.println(getTag("I") + " " + message);
    }

    public void w(String message) {
        System.out.println(getTag("W") + " " + message);
    }

    public void e(String message) {
        System.out.println(getTag("E") + " " + message);
    }

    private String getTag(String severity) {
        String fullClassName = Thread.currentThread().getStackTrace()[4].getClassName();
        String className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1);
        String methodName = Thread.currentThread().getStackTrace()[4].getMethodName();
        int lineNumber = Thread.currentThread().getStackTrace()[4].getLineNumber();

        return severity + ": " + tagPrefix + className + "" + methodName + "():" + lineNumber;
    }
}
