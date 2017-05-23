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

package wvlet.surface

object ClassSurfaceTest {

  class A(val id:Int)(implicit val context:String)

}

import ClassSurfaceTest._
class ClassSurfaceTest extends SurfaceSpec {

  "Surface for Class" should {

    "support multiple param blocks" in {
      val a = check(Surface.of[A], "A")
      info(a.params.mkString(", "))

      a.params.length shouldBe 2

      val p0 = a.params(0)
      val p1 = a.params(1)
      p0.name shouldBe "id"
      p1.name shouldBe "context"

      val a0 = a.objectFactory.map { x =>
        x.newInstance(Seq(1, "c"))
      }.get.asInstanceOf[A]

      a0.id shouldBe 1
      a0.context shouldBe "c"
    }
  }
}

