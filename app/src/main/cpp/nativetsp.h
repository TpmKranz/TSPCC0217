#ifndef JNI_H
#include <jni.h>
#endif

typedef void * ptr;

jbyte * mutate(
    jint k,
    jbyte * perm,
    jbyte PROBLEM_SIZE,
    jint * FACTORIALS,
    ptr * interval
  );

jint isqrt(jint radicand);
