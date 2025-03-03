package me.stasmarkin.rockpaperscissors.utils

inline fun silently(
  onError: (Exception) -> Unit = {},
  block: () -> Unit
) {
  try {
    block()
  } catch (e: Exception) {
    onError(e)
  }
}