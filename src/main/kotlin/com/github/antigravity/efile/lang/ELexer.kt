package com.github.antigravity.efile.lang

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

class ELexer : LexerBase() {
    private var myBuffer: CharSequence = ""
    private var myStartOffset: Int = 0
    private var myEndOffset: Int = 0
    private var myTokenStart: Int = 0
    private var myTokenEnd: Int = 0
    private var myTokenType: IElementType? = null
    private var myState: Int = 0

    companion object {
        const val STATE_DEFAULT = 0
        const val STATE_IN_TAG = 1
    }

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        myBuffer = buffer
        myStartOffset = startOffset
        myEndOffset = endOffset
        myTokenStart = startOffset
        myState = initialState
        advance()
    }

    override fun getState(): Int = myState

    override fun getTokenType(): IElementType? = myTokenType

    override fun getTokenStart(): Int = myTokenStart

    override fun getTokenEnd(): Int = myTokenEnd

    override fun advance() {
        myTokenStart = if (myTokenType == null) myStartOffset else myTokenEnd
        if (myTokenStart >= myEndOffset) {
            myTokenType = null
            return
        }

        val c = myBuffer[myTokenStart]

        // Handle whitespace
        if (Character.isWhitespace(c)) {
            var i = myTokenStart + 1
            while (i < myEndOffset && Character.isWhitespace(myBuffer[i])) {
                i++
            }
            myTokenType = ETokenTypes.WHITE_SPACE
            myTokenEnd = i
            return
        }

        // Handle Comments // - 只将 // 作为 MARKER_COMMENT，后面的内容按普通字段解析
        // 这样注释行的字段说明可以按列对齐
        if (c == '/' && myTokenStart + 1 < myEndOffset && myBuffer[myTokenStart + 1] == '/') {
            myTokenType = ETokenTypes.MARKER_COMMENT
            myTokenEnd = myTokenStart + 2
            return
        }
        
        // Handle Tags
        if (c == '<') {
            myState = STATE_IN_TAG
            if (myTokenStart + 1 < myEndOffset) {
                val next = myBuffer[myTokenStart + 1]
                if (next == '/') {
                    myTokenType = ETokenTypes.TAG_CLOSE_START
                    myTokenEnd = myTokenStart + 2
                    return
                } else if (next == '!') {
                     myTokenType = ETokenTypes.TAG_INFO_START
                     myTokenEnd = myTokenStart + 2
                     return
                }
            }
            myTokenType = ETokenTypes.TAG_START
            myTokenEnd = myTokenStart + 1
            return
        }
        
        if (c == '>') {
             myState = STATE_DEFAULT
             myTokenType = ETokenTypes.TAG_END
             myTokenEnd = myTokenStart + 1
             return
        }
        
        if (c == '!' && myTokenStart + 1 < myEndOffset && myBuffer[myTokenStart + 1] == '>') {
             myState = STATE_DEFAULT
             myTokenType = ETokenTypes.TAG_INFO_END
             myTokenEnd = myTokenStart + 2
             return
        }

        // Handle Strings '...' - Strictly handled here if starting with '
        if (c == '\'') {
            var i = myTokenStart + 1
            while (i < myEndOffset) {
                if (myBuffer[i] == '\'') {
                    i++
                    break
                }
                i++
            }
            myTokenType = ETokenTypes.STRING
            myTokenEnd = i
            return
        }

        // Handle Markers
        if (c == '@') {
            if (myTokenStart + 1 < myEndOffset && myBuffer[myTokenStart + 1] == '@') {
                myTokenType = ETokenTypes.MARKER_AT_AT
                myTokenEnd = myTokenStart + 2
            } else {
                myTokenType = ETokenTypes.MARKER_AT
                myTokenEnd = myTokenStart + 1
            }
            return
        }
        if (c == '#') {
            myTokenType = ETokenTypes.MARKER_HASH
            myTokenEnd = myTokenStart + 1
            return
        }
         if (c == '$') {
            myTokenType = ETokenTypes.MARKER_DOLLAR
            myTokenEnd = myTokenStart + 1
            return
        }
         if (c == '%') {
            myTokenType = ETokenTypes.MARKER_PERCENT
            myTokenEnd = myTokenStart + 1
            return
        }

        // We allow . : = / inside identifiers now, so we removed their specific checks here.
        // except :: which is structural.
        
        if (c == ':' && myTokenStart + 1 < myEndOffset && myBuffer[myTokenStart + 1] == ':') {
             myTokenType = if (myState == STATE_IN_TAG) ETokenTypes.TAG_IDENTIFIER else ETokenTypes.DOUBLE_COLON
             myTokenEnd = myTokenStart + 2
             return
        }
        // If : is alone, it is just a character.

        // All other characters start an IDENTIFIER.
        // We consume until whitespace or structural boundary.
        
        myTokenType = if (myState == STATE_IN_TAG) ETokenTypes.TAG_IDENTIFIER else ETokenTypes.IDENTIFIER
        var i = myTokenStart
        while (i < myEndOffset) {
            val k = myBuffer[i]
            
            // Break on ANY whitespace
            if (Character.isWhitespace(k)) break
            
            // Break on Structural Markers
            if (k == '<' || k == '>') {
                // If we are at start (i==myTokenStart), handled by main block.
                // If inside, break.
                if (i > myTokenStart) break
            }
            
            // Single Quotes:
            // If we encounter ' in middle? e.g. "don't". 
            // User says "Exception: content inside single quotes is one field".
            // Typically implies ' starts a string. 
            // If we see ' inside, strictly speaking it starts a new token STRING.
            if (k == '\'') break
            
            // Break on Comment //
            if (k == '/' && i + 1 < myEndOffset && myBuffer[i+1] == '/') break
            
            i++
        }
        
        // Ensure progress
        if (i == myTokenStart) {
             i++
        }
        
        myTokenEnd = i
        return
    }

    override fun getBufferSequence(): CharSequence = myBuffer

    override fun getBufferEnd(): Int = myEndOffset
}
