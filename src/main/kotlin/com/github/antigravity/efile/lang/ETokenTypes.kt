package com.github.antigravity.efile.lang

import com.github.antigravity.efile.ELanguage
import com.intellij.psi.tree.IElementType
import com.intellij.psi.TokenType

class EElementType(debugName: String) : IElementType(debugName, ELanguage)

object ETokenTypes {
    val COMMENT = EElementType("COMMENT") // // comment (legacy, keep for compatibility)
    val TAG_START = EElementType("TAG_START") // <
    val TAG_END = EElementType("TAG_END") // >
    val TAG_CLOSE_START = EElementType("TAG_CLOSE_START") // </
    val TAG_INFO_START = EElementType("TAG_INFO_START") // <!
    val TAG_INFO_END = EElementType("TAG_INFO_END") // !>

    val MARKER_AT = EElementType("MARKER_AT") // @
    val MARKER_AT_AT = EElementType("MARKER_AT_AT") // @@
    val MARKER_HASH = EElementType("MARKER_HASH") // #
    val MARKER_DOLLAR = EElementType("MARKER_DOLLAR") // $
    val MARKER_PERCENT = EElementType("MARKER_PERCENT") // %
    val MARKER_COMMENT = EElementType("MARKER_COMMENT") // // (用于注释行按列对齐)

    val STRING = EElementType("STRING") // 'string'
    val NUMBER = EElementType("NUMBER")
    val IDENTIFIER = EElementType("IDENTIFIER")
    val TAG_IDENTIFIER = EElementType("TAG_IDENTIFIER")
    val DOT = EElementType("DOT") // .
    val DOUBLE_COLON = EElementType("DOUBLE_COLON") // ::
    val EQ = EElementType("EQ") // =

    val MODULE = EElementType("MODULE") // <...> ... </...>
    val DATA_SECTION = EElementType("DATA_SECTION") // @ ...
    val DATA_ROW = EElementType("DATA_ROW") // # ...

    val WHITE_SPACE = TokenType.WHITE_SPACE
    val BAD_CHARACTER = TokenType.BAD_CHARACTER
}
