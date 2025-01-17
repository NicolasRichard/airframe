/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wvlet.airframe

import wvlet.airframe.surface.reflect.ReflectSurfaceFactory

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
  */
package object surface {
  val surfaceCache       = new ConcurrentHashMap[String, Surface]().asScala
  val methodSurfaceCache = new ConcurrentHashMap[String, Seq[MethodSurface]]().asScala

  def getCached(fullName: String): Surface = ReflectSurfaceFactory.get(fullName)
  def newCacheMap[A, B]: mutable.Map[A, B] = new mutable.WeakHashMap[A, B]()
}
