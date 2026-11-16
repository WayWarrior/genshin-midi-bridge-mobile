package com.android.internal.util;public class Preconditions{public static void checkArgument(boolean b,String msg){if(!b)throw new IllegalArgumentException(msg);}}
