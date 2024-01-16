package io.appform.memq.utils;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Set;

@UtilityClass
public class CommonUtils {

    public boolean isEmpty(Collection<?> collection) {
        return null == collection || collection.isEmpty();
    }

    public boolean isRetriable(Set<String> retriableExceptions, Throwable exception) {
        return CommonUtils.isEmpty(retriableExceptions)
                || (null != exception
                && retriableExceptions.contains(exception.getClass().getSimpleName()));
    }
}