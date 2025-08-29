package com.mobifone.vdi.utils;

import java.util.*;

public class H {

    public static <T> void each(List<T> list, Each<T> each) throws RuntimeException {
        if (!H.isTrue(list)) {
            return;
        }

        for (int index = 0; index < list.size(); index++) {
            each.do_(index, list.get(index));
        }
    }

    public static <T> List<T> findAll(List<T> list, final Find<T> findAll_) throws RuntimeException {
        final List<T> newList = new ArrayList<T>();

        if (!H.isTrue(list)) {
            return newList;
        }

        try {
            each(
                list,
                (index, item) -> {
                    if (findAll_.do_(index, item)) {
                        newList.add(item);
                    }
                }
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return newList;
    }

    public static <T> T find(List<T> list, final Find<T> findAll_) {
        if (!H.isTrue(list)) {
            return null;
        }

        for (int index = 0; index < list.size(); index++) {
            if (findAll_.do_(index, list.get(index))) {
                return list.get(index);
            }
        }

        return null;
    }

    public static <T> T remove(List<T> list, final Find<T> findAll_) {
        if (!H.isTrue(list)) {
            return null;
        }

        List<T> matchedItems = H.findAll(list, findAll_);
        if (H.isTrue(matchedItems)) {
            list.removeAll(matchedItems);
        }

        return null;
    }

    public static <T> Boolean exists(List<T> list, final Find<T> findAll_) {
        if (!H.isTrue(list)) {
            return false;
        }

        for (int index = 0; index < list.size(); index++) {
            if (findAll_.do_(index, list.get(index))) {
                return true;
            }
        }

        return false;
    }

    public static <T1, T2> List<T2> collect(List<T1> list, Collect<T1, T2> collect) {
        List<T2> newList = new ArrayList<T2>();

        if (!H.isTrue(list)) {
            return newList;
        }

        for (int index = 0; index < list.size(); index++) {
            newList.add(collect.do_(index, list.get(index)));
        }

        return newList;
    }

    public static String join(Collection<String> strs, String delimiter) {
        if (strs == null || strs.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        Iterator<String> iterator = strs.iterator();
        while (iterator.hasNext()) {
            sb.append(iterator.next());
            if (iterator.hasNext()) {
                sb.append(delimiter);
            }
        }
        return sb.toString();
    }

    public static String join(String[] strs, String delimiter) {
        if (strs == null || strs.length == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        for (int index = 0; index < strs.length; index++) {
            sb.append(strs[index]);

            if (index < strs.length - 1) {
                sb.append(delimiter);
            }
        }

        return sb.toString();
    }

    public static Boolean isTrue(Object value) {
        if (value == null) {
            return false;
        }

        if (value instanceof String) {
            return !((String) value).trim().isEmpty();
        }

        if (value instanceof Number) {
            return !((Number) value).equals(0L);
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof Collection) {
            return !((Collection) value).isEmpty();
        }

        if (value instanceof Object[]) {
            return ((Object[]) value).length > 0;
        }

        return true;
    }

    /**
     * @param object
     * @param nvls
     * @param <T>
     * @return trả về giá trị object nếu object != null , còn không trả về giá trị nvl
     */
    public static <T> T nvl(T object, NVL<T>... nvls) {
        int index = 0;
        if (nvls != null) {
            while (object == null && index < nvls.length) {
                object = nvls[index].getDefaultValue();
                index++;
            }
        }
        return object;
    }

    public static <T> T nvl(T object, T... nvls) {
        int index = 0;
        if (nvls != null) {
            while (object == null && index < nvls.length) {
                object = nvls[index];
                index++;
            }
        }
        return object;
    }

    public static <T> T getOne(List<T> list) {
        return H.isTrue(list) ? list.get(0) : null;
    }

    public static <T> void ifNotNull(T obj, IfNotNull<T> tIfNotNull) {
        if (H.isTrue(obj)) {
            tIfNotNull.do_(obj);
        }
    }

    public static <T1, T2> Boolean isDiff(List<T1> list1, List<T2> list2) {
        return !isDiff(list1, list2, Objects::equals);
    }

    public static <T1, T2> Boolean isDiff(List<T1> list1, List<T2> list2, IsMatch<T1, T2> matchLogic) {
        if (!H.isTrue(list1) && !H.isTrue(list2)) {
            return false; //2 list đều rỗng;
        }
        if (!H.isTrue(list1) || !H.isTrue(list2)) {
            return true; //1 trong 2 list rỗng;
        }
        if (list1.size() != list2.size()) {
            return true;
        }
        return H.exists(list1, (index, item1) -> !H.exists(list2, (index1, item2) -> matchLogic.do_(item1, item2)));
    }

    @SafeVarargs
    public static <T> List<T> join(Collection<T>... elements) {
        if (elements == null) {
            return null;
        }
        List<T> elements_ = new ArrayList<>();
        for (Collection<T> element : elements) {
            if (H.isTrue(elements)) {
                elements_.addAll(element);
            }
        }
        return elements_;
    }

    public static <T> Map groupBy(List<T> list, List<String> strs) {
        if (strs == null || strs.isEmpty() || list == null) {
            return null;
        }
        Map map = new HashMap<>();
        try {
            for (int t = 0; t < strs.size(); t++) {
                for (int i = 0; i < list.size(); i++) {
                    Object key = list.get(i).getClass().getField(strs.get(t));
                    Object list1 = map.get(key);
                    if (list1 == null) {
                        map.put(key, list.get(i));
                    }
                }
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return map;
    }

    //    public static Pageable sortAgain(Pageable pageable, String key) {
    //        return new PageRequest(
    //                pageable.getPageNumber(),
    //                pageable.getPageSize(),
    //                pageable.getSort()
    //                        .and(new Sort(Sort.Direction.ASC, key))
    //        );
    //    }

    public interface Each<T> {
        void do_(int index, T item) throws RuntimeException;
    }

    public interface Collect<T1, T2> {
        T2 do_(int index, T1 item) throws RuntimeException;
    }

    public interface IsMatch<T1, T2> {
        Boolean do_(T1 value1, T2 value2) throws RuntimeException;
    }

    public interface Find<T> {
        Boolean do_(int index, T item) throws RuntimeException;
    }

    public interface NVL<T> {
        T getDefaultValue() throws RuntimeException;
    }

    public interface IfNotNull<T> {
        void do_(T obj) throws RuntimeException;
    }
}
