/*
 * Copyright 2019 Josip Grgurica and Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.jkobejs.zio.google.cloud.oauth2.webserver.oauthclientkey

import java.nio.file.{InvalidPathException, NoSuchFileException, Paths}

import cats.effect.Blocker
import io.circe.generic.auto._
import io.circe.parser.decode
import io.github.jkobejs.zio.google.cloud.oauth2.webserver.oauthclientkey.OAuthClientKeyError.{
  FileDoesNotExist,
  InvalidJsonFormat,
  InvalidPathError
}
import io.github.jkobejs.zio.google.cloud.oauth2.webserver.oauthclientkey.OAuthClientKeyReader.Service
import zio.blocking.Blocking
import zio.interop.catz._
import zio.{RIO, Task, ZIO}

trait FS2OAuthClientKeyReader extends OAuthClientKeyReader with Blocking {
  override val oAuthClientKeyReader: Service[Any] = new Service[Any] {
    override def readKey(path: String): ZIO[Any, OAuthClientKeyError, OAuthClientKey] =
      blocking.blockingExecutor.flatMap(
        executor =>
          RIO
            .effect(Paths.get(path))
            .refineOrDie {
              case _: InvalidPathException => InvalidPathError(path)
            }
            .flatMap(
              jPath =>
                fs2.io.file
                  .readAll[Task](jPath, Blocker.liftExecutionContext(executor.asEC), 4096)
                  .through(fs2.text.utf8Decode)
                  .compile
                  .toList
                  .map(_.mkString)
                  .refineOrDie {
                    case _: NoSuchFileException => FileDoesNotExist(path)
                  }
                  .flatMap(
                    string =>
                      RIO
                        .fromEither(decode[Web](string))
                        .map(_.web)
                        .asError(InvalidJsonFormat(path))
                  )
            )
            .refineOrDie {
              case error: OAuthClientKeyError => error
            }
      )
  }
}
