/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsByteLiteralCharactersInspectionTest : RsInspectionsTestBase(RsByteLiteralCharactersInspection::class) {

    fun `test not applicable for ASCII characters in byte literal`() = checkErrors("""
        fn main() {
            let a = b'a';
        }
    """)

    fun `test not applicable for ASCII characters in byte string literal`() = checkErrors("""
        fn main() {
            let a = b" ~";
        }
    """)

    fun `test non-ASCII symbol in byte literal`() = checkErrors("""
        fn main() {
            let a = <error descr="Non-ASCII character in byte literal">b'é'</error>;
        }
    """)

    fun `test non-ASCII symbol in byte string literal`() = checkErrors("""
        fn main() {
            let a = <error descr="Non-ASCII character in byte string literal">b"👨‍👩‍👦"</error>;
        }
    """)

    fun `test fix`() = checkFixByText("Replace non-ASCII symbols", """
        fn main() {
            let a = <error descr="Non-ASCII character in byte string literal"><caret>b"👨‍👩‍👦 and 🥺"</error>;
        }
    """, """
        fn main() {
            let a = b"\xf0\x9f\x91\xa8\xe2\x80\x8d\xf0\x9f\x91\xa9\xe2\x80\x8d\xf0\x9f\x91\xa6 and \xf0\x9f\xa5\xba";
        }
    """)
}
