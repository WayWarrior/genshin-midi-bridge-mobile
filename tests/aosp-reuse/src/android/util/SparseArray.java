package android.util;import java.util.*;public class SparseArray<T>{Map<Integer,T>a=new HashMap<>();public T get(int k,T d){return a.getOrDefault(k,d);}public void put(int k,T v){a.put(k,v);}}
