#include <stdint.h>
#include <stdlib.h>
#include <jni.h>
#include <android/log.h>
#include <math.h>
#include "nativetsp.h"

jbyteArray Java_org_tpmkranz_tsp_BruteforceInterval_bruteforce(
    JNIEnv *env,
    jobject this,
    jint start,
    jint end,
    jintArray distances
  ) {
  jint number_of_distances = (*env)->GetArrayLength(env, distances);
  jbyte problem_size = ((jbyte) isqrt(number_of_distances)) - 1;
  if ((problem_size+1)*(problem_size+1) != number_of_distances) {
    jclass e = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    (*env)->ThrowNew(env, e, "Distance matrix malformed.");
    return NULL;
  }
  if (problem_size > 12) {
    jclass e = (*env)->FindClass(env, "java/lang/NumberFormatException");
    (*env)->ThrowNew(env, e, "Distance matrix too big.");
    return NULL;
  }

  jint * factorials = malloc((problem_size+1) * sizeof(jint));
  if (factorials == NULL) {
    jclass e = (*env)->FindClass(env, "java/lang/NullPointerException");
    (*env)->ThrowNew(env, e, "Unable to allocate factorial array memory.");
    return NULL;
  }
  factorials[0] = 1;
  for (jbyte i = 1; i <= problem_size; i++) {
    factorials[i] = i * factorials[i-1];
  }
  if (start <= 0 || end > factorials[problem_size]) {
    jclass e = (*env)->FindClass(env, "java/lang/IndexOutOfBoundsException");
    (*env)->ThrowNew(env, e, "Start and end values must be greater than 0 and less than or equal to (problem_size)!.");
    return NULL;
  }
  ptr * interval = malloc(problem_size * sizeof(ptr));
  jbyte * perm = malloc(problem_size * sizeof(jbyte));
  if (interval == NULL || perm == NULL) {
    jclass e = (*env)->FindClass(env, "java/lang/NullPointerException");
    (*env)->ThrowNew(env, e, "Unable to allocate permutation array memory.");
    return NULL;
  }

  jint * distance_matrix = (*env)->GetIntArrayElements(env, distances, NULL);
  if (distance_matrix == NULL) {
    jclass e = (*env)->FindClass(env, "java/lang/NullPointerException");
    (*env)->ThrowNew(env, e, "Unable to access original distance matrix.");
    return NULL;
  }
  jint minimum_distance = INT32_MAX;
  jint shortest_permutation = 0;
  for (jint k = start; k <= end; k++) {
    mutate(k, perm, problem_size, factorials, interval);
    jint distance = distance_matrix[perm[0]];
    for (jbyte j = 0; j < problem_size - 1; j++) {
      distance += distance_matrix[perm[j]*(problem_size+1)+perm[j+1]];
    }
    distance += distance_matrix[perm[problem_size-1]*(problem_size+1)];
    if (distance < minimum_distance) {
      minimum_distance = distance;
      shortest_permutation = k;
    }
  }
  (*env)->ReleaseIntArrayElements(env, distances, distance_matrix, JNI_ABORT);
  mutate(shortest_permutation, perm, problem_size, factorials, interval);
  jbyteArray result = (*env)->NewByteArray(env, problem_size);
  if (result == NULL) {
    jclass e = (*env)->FindClass(env, "java/lang/NullPointerException");
    (*env)->ThrowNew(env, e, "Unable to construct result array.");
    return NULL;
  }
  (*env)->SetByteArrayRegion(env, result, 0, problem_size, perm);
  return result;
}

/* Writes the k-th permutation of {1,...,n} into perm. */
jbyte * mutate(
    jint k,
    jbyte * perm,
    jbyte PROBLEM_SIZE,
    jint * FACTORIALS,
    ptr * interval
  ) {
  /* There are only n! permutations and we index them starting with 1. */
  if (k < 1 || k > FACTORIALS[PROBLEM_SIZE]) {
    return NULL;
  }
  /* What I said above. */
  for (jbyte i = 0; i < PROBLEM_SIZE; i++) {
    interval[i] = (ptr) (interval+i+1);
  }
  /*
  * Now it gets a bit complicated.
  * See, interval represents a linked list, with interval itself being the first
  * element and *(interval) being the second. Whenever an element is removed,
  * its predecessor's content is set to that element's content, because the
  * dereferencing operation represents getting an element's successor in the
  * list and traversing the list is done by repeating the dereferencing. The
  * k-th element is thus the (pointer arithmetic) difference between the k-th
  * dereference of list (which starts out as interval) and interval itself
  * (+1 if your list is supposed to be {1,...,n}).
  */
  ptr * list = interval;
  for (jbyte i = PROBLEM_SIZE-1; i > 0; i--) {
    /*
    * For the algorithm used to obtain the k-th permutaion, see:
    * http://math.stackexchange.com/a/60760
    */
    jint index = k / FACTORIALS[i];
    // Correct the index if k % i! == 0.
    index -= (index * FACTORIALS[i]) / k;
    k -= index * FACTORIALS[i];
    // The very first "element" is interval itself.
    ptr element = (ptr) list;
    // Its predecessor's content points to itself again.
    ptr * predecessor = (ptr *) &list;
    // Traverse the list up until the index-th element.
    for (jint j = 0; j < index; j++) {
      predecessor = (ptr *) element;
      element = *((ptr *) element);
    }
    // And make the element's predecessor's successor the element's successor.
    *(predecessor) = *((ptr *) element);
    // Now we have the (PROBLEM_SIZE-i)-th element in the k-th permutation.
    perm[PROBLEM_SIZE-i-1] = (jbyte)((ptr *)element - interval) + 1;
  }
  // And the last element is the remaining (now first) one in the list.
  perm[PROBLEM_SIZE-1] = (jbyte)(list - interval) + 1;
  return perm;
}

jint isqrt(jint radicand) {
  jint r = 0;
  while (r*r <= radicand) {
    r++;
  }
  return r-1;
}
