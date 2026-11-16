package android.os; public interface Parcelable {int describeContents();void writeToParcel(Parcel p,int f);interface Creator<T>{T createFromParcel(Parcel p);T[] newArray(int n);}}
