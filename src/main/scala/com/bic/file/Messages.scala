package com.bic.file

import java.nio.file.WatchEvent.Modifier
import java.nio.file._

import com.bic.file.RegistryTypes._

/**
 * Message definitions
 */
object Messages {

  sealed case class EventInPath(event: WatchEvent.Kind[_], path: Path)

  sealed case class RegisterCallback(
                                      event: WatchEvent.Kind[Path],
                                      modifier: Option[Modifier] = None,
                                      recursive: Boolean = false,
                                      path: Path,
                                      callback: Callback)

  sealed case class UnRegisterCallback(
                                      event: WatchEvent.Kind[Path],
                                      modifier: Option[Modifier] = None,
                                      recursive: Boolean = false,
                                      path: Path,
                                      callback: Callback)


}
