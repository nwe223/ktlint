package com.pinterest.ktlint.ruleset.standard.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.ANNOTATION
import com.pinterest.ktlint.rule.engine.core.api.ElementType.ANNOTATION_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.EQ
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.children
import com.pinterest.ktlint.rule.engine.core.api.isPartOf
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment
import com.pinterest.ktlint.rule.engine.core.api.isPartOfString
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpace
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithNewline
import com.pinterest.ktlint.rule.engine.core.api.nextLeaf
import com.pinterest.ktlint.rule.engine.core.api.nextSibling
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceAfterMe
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceBeforeMe
import com.pinterest.ktlint.ruleset.standard.StandardRule
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeConstraint
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

public class SpacingAroundColonRule : StandardRule("colon-spacing") {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        if (node is LeafPsiElement && node.textMatches(":") && !node.isPartOfString() && !node.isPartOfComment()) {
            if (node.isPartOf(ANNOTATION) || node.isPartOf(ANNOTATION_ENTRY)) {
                // todo: enforce "no spacing"
                return
            }
            val removeSpacingBefore =
                node.parent !is KtClassOrObject &&
                    node.parent !is KtConstructor<*> && // constructor : this/super
                    node.parent !is KtTypeConstraint && // where T : S
                    node.parent?.parent !is KtTypeParameterList
            val prevLeaf = node.prevLeaf()
            if (prevLeaf != null && prevLeaf.isWhiteSpaceWithNewline()) {
                emit(prevLeaf.startOffset, "Unexpected newline before \":\"", true)
                if (autoCorrect) {
                    val parent = node.parent
                    val prevNonCodeElements =
                        node
                            .siblings(forward = false, withItself = false)
                            .takeWhile { it.node.isWhiteSpace() || it.node.isPartOfComment() }
                            .toList()
                            .reversed()
                    when {
                        parent is KtProperty || parent is KtNamedFunction -> {
                            val equalsSignElement =
                                node
                                    .node
                                    .siblings(forward = true)
                                    .firstOrNull { it.elementType == EQ }
                            if (equalsSignElement != null) {
                                equalsSignElement
                                    .treeNext
                                    ?.let { treeNext ->
                                        prevNonCodeElements.forEach {
                                            parent.node.addChild(it.node, treeNext)
                                        }
                                        if (treeNext.isWhiteSpace()) {
                                            equalsSignElement.treeParent.removeChild(treeNext)
                                        }
                                        Unit
                                    }
                            }
                            val blockElement =
                                node
                                    .siblings(forward = true, withItself = false)
                                    .firstIsInstanceOrNull<KtBlockExpression>()
                                    ?.node
                            if (blockElement != null) {
                                val before =
                                    blockElement
                                        .firstChildNode
                                        .nextSibling()
                                prevNonCodeElements
                                    .let {
                                        if (it.first().node.isWhiteSpace()) {
                                            blockElement.treeParent.removeChild(it.first().node)
                                            it.drop(1)
                                        } else {
                                            blockElement.addChild(PsiWhiteSpaceImpl("#"), before)
                                            it
                                        }
                                        if (it.last().node.isWhiteSpaceWithNewline()) {
                                            blockElement.treeParent.removeChild(it.last().node)
                                            it.dropLast(1)
                                        } else {
                                            it
                                        }
                                    }.forEach {
                                        blockElement.addChild(it.node, before)
                                    }
                            }
                            if (prevNonCodeElements.first() != prevNonCodeElements.last()) {
//                                parent.deleteChildRange(prevNonCodeElements.first(), prevNonCodeElements.last())
//                                parent.node.removeRange(prevNonCodeElements.first().node, prevNonCodeElements.last().node)
                                Unit
                            } else {
//                                parent.node.removeChild(prevNonCodeElements.first().node)
                                Unit
                            }
                        }
                        prevLeaf.prevLeaf()?.isPartOfComment() == true -> {
                            val nextLeaf = node.nextLeaf()
                            prevNonCodeElements.forEach {
                                node.treeParent.addChild(it.node, nextLeaf)
                            }
                            if (nextLeaf != null && nextLeaf.isWhiteSpace()) {
                                node.treeParent.removeChild(nextLeaf)
                            }
                        }
                        else -> {
                            val text = prevLeaf.text
                            if (removeSpacingBefore) {
                                prevLeaf.treeParent.removeChild(prevLeaf)
                            } else {
                                (prevLeaf as LeafPsiElement).rawReplaceWithText(" ")
                            }
                            (node as ASTNode).upsertWhitespaceAfterMe(text)
                        }
                    }
                }
            }
            if (node.prevSibling is PsiWhiteSpace && removeSpacingBefore && !prevLeaf.isWhiteSpaceWithNewline()) {
                emit(node.startOffset, "Unexpected spacing before \":\"", true)
                if (autoCorrect) {
                    node.prevSibling.node.treeParent.removeChild(node.prevSibling.node)
                }
            }
            val missingSpacingBefore =
                node.prevSibling !is PsiWhiteSpace &&
                    (
                        node.parent is KtClassOrObject || node.parent is KtConstructor<*> ||
                            node.parent is KtTypeConstraint || node.parent.parent is KtTypeParameterList
                        )
            val missingSpacingAfter = node.nextSibling !is PsiWhiteSpace
            when {
                missingSpacingBefore && missingSpacingAfter -> {
                    emit(node.startOffset, "Missing spacing around \":\"", true)
                    if (autoCorrect) {
                        (node as ASTNode).upsertWhitespaceBeforeMe(" ")
                        (node as ASTNode).upsertWhitespaceAfterMe(" ")
                    }
                }
                missingSpacingBefore -> {
                    emit(node.startOffset, "Missing spacing before \":\"", true)
                    if (autoCorrect) {
                        (node as ASTNode).upsertWhitespaceBeforeMe(" ")
                    }
                }
                missingSpacingAfter -> {
                    emit(node.startOffset + 1, "Missing spacing after \":\"", true)
                    if (autoCorrect) {
                        (node as ASTNode).upsertWhitespaceAfterMe(" ")
                    }
                }
            }
        }
    }
}

public val SPACING_AROUND_COLON_RULE_ID: RuleId = SpacingAroundColonRule().ruleId
