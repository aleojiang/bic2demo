package com.bic.file

import java.nio.file.Path

/**
 * Created by patrick.jiang on 7/2/14.
 *
 */
object RegistryTypes {
  type Callback  = (Path) => Unit
  type Callbacks = List[Callback]
}
