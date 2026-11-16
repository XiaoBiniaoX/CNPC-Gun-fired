package bincnpc.gun.common.legacy;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import java.util.stream.*;

/** Java 8 equivalents used by the unchanged common/editor algorithms. */
public final class Legacy {
    private Legacy() {}
    @SafeVarargs public static <T> List<T> list(T... values) {
        ArrayList<T> result = new ArrayList<T>(values.length);
        for (T value : values) result.add(Objects.requireNonNull(value));
        return Collections.unmodifiableList(result);
    }
    @SafeVarargs public static <T> Set<T> set(T... values) {
        LinkedHashSet<T> result = new LinkedHashSet<T>();
        for (T value : values) if (!result.add(Objects.requireNonNull(value))) throw new IllegalArgumentException("duplicate set value");
        return Collections.unmodifiableSet(result);
    }
    @SuppressWarnings("unchecked") public static <K,V> Map<K,V> map(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("map entries require pairs");
        LinkedHashMap<K,V> result = new LinkedHashMap<K,V>();
        for (int i=0;i<entries.length;i+=2) {
            K key=(K)Objects.requireNonNull(entries[i]); V value=(V)Objects.requireNonNull(entries[i+1]);
            if (result.put(key,value)!=null) throw new IllegalArgumentException("duplicate map key");
        }
        return Collections.unmodifiableMap(result);
    }
    public static <T> List<T> copyList(Collection<? extends T> values) { return Collections.unmodifiableList(new ArrayList<T>(values)); }
    public static <T> Set<T> copySet(Collection<? extends T> values) { return Collections.unmodifiableSet(new LinkedHashSet<T>(values)); }
    public static <K,V> Map<K,V> copyMap(Map<? extends K,? extends V> values) { return Collections.unmodifiableMap(new LinkedHashMap<K,V>(values)); }
    public static <T> Collector<T,?,List<T>> toList() { return Collectors.collectingAndThen(Collectors.<T>toList(), Collections::unmodifiableList); }
    public static List<JsonElement> jsonList(JsonArray array) { ArrayList<JsonElement> values=new ArrayList<JsonElement>(); for(JsonElement value:array)values.add(value);return values; }
    public static <K,V> Set<K> keys(Map<K,V> object) { return object.keySet(); }
    public static <T> T[] array(Collection<? extends T> values, java.util.function.IntFunction<T[]> factory) { return values.toArray(factory.apply(values.size())); }
    public static <T> T[] array(Stream<? extends T> values, java.util.function.IntFunction<T[]> factory) { return values.toArray(factory); }
    public static Set<String> keys(JsonObject object) { Set<String> result = new LinkedHashSet<String>(); for (Map.Entry<String, JsonElement> entry : object.entrySet()) result.add(entry.getKey()); return result; }
    public static JsonElement copy(JsonElement value) {
        if(value==null||value.isJsonNull())return JsonNull.INSTANCE;
        if(value.isJsonObject())return copy(value.getAsJsonObject());
        if(value.isJsonArray())return copy(value.getAsJsonArray());
        JsonPrimitive p=value.getAsJsonPrimitive();
        return p.isBoolean()?new JsonPrimitive(p.getAsBoolean()):p.isNumber()?new JsonPrimitive(p.getAsNumber()):new JsonPrimitive(p.getAsString());
    }
    public static JsonObject copy(JsonObject value) { JsonObject result=new JsonObject();for(Map.Entry<String,JsonElement> entry:value.entrySet())result.add(entry.getKey(),copy(entry.getValue()));return result; }
    public static JsonArray copy(JsonArray value) { JsonArray result=new JsonArray();for(JsonElement entry:value)result.add(copy(entry));return result; }
    public static String repeat(String value,int times) { if(times<0)throw new IllegalArgumentException("negative repeat count");StringBuilder result=new StringBuilder();for(int i=0;i<times;i++)result.append(value);return result.toString(); }
    public static byte[] readAll(InputStream stream) throws IOException { ByteArrayOutputStream result=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;while((count=stream.read(buffer))!=-1)result.write(buffer,0,count);return result.toByteArray(); }
}
