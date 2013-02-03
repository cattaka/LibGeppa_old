
package net.cattaka.libgeppa;

import java.lang.reflect.Field;

import net.cattaka.libgeppa.GeppaSocket.ReadState;

public class TestUtil {
    public static int pickInt(Object obj, String name) throws Exception {
        Class<?> c = obj.getClass();
        Field fld = c.getDeclaredField(name);
        fld.setAccessible(true);

        return fld.getInt(obj);
    }

    public static ReadState pickReadState(Object obj, String name) throws Exception {
        Class<?> c = obj.getClass();
        Field fld = c.getDeclaredField(name);
        fld.setAccessible(true);

        return (ReadState)fld.get(obj);
    }

    public static byte[] pickBytes(Object obj, String name) throws Exception {
        Class<?> c = obj.getClass();
        Field fld = c.getDeclaredField(name);
        fld.setAccessible(true);

        return (byte[])fld.get(obj);
    }

    public static GeppaFragment[] pickGeppaFragment(Object obj, String name) throws Exception {
        Class<?> c = obj.getClass();
        Field fld = c.getDeclaredField(name);
        fld.setAccessible(true);

        return (GeppaFragment[])fld.get(obj);
    }
}
