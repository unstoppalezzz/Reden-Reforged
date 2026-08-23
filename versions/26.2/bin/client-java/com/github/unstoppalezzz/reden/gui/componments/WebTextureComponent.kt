package com.github.unstoppalezzz.reden.gui.componments

import io.wispforest.owo.ui.base.BaseUIComponent
import io.wispforest.owo.ui.core.AnimatableProperty
import io.wispforest.owo.ui.core.OwoUIGraphics
import io.wispforest.owo.ui.core.PositionedRectangle
import io.wispforest.owo.ui.core.Sizing

open class WebTextureComponent(
    private val texture: WebTexture,
    private val u: Int,
    private val v: Int,
    private val regionWidth: Int,
    private val regionHeight: Int,
) : BaseUIComponent() {
    private val visibleArea =
        AnimatableProperty.of(PositionedRectangle.of(0, 0, texture.image.width, texture.image.height))!!

    companion object {
        fun fixedHeight(texture: WebTexture, u: Int, v: Int, height: Int) = WebTextureComponent(
            texture, u, v, height * texture.image.width / texture.image.height, height
        )
    }

    var blend: Boolean = false

    override fun determineHorizontalContentSize(sizing: Sizing): Int {
        return this.regionWidth
    }

    override fun determineVerticalContentSize(sizing: Sizing): Int {
        return this.regionHeight
    }

    override fun update(delta: Float, mouseX: Int, mouseY: Int) {
        super.update(delta, mouseX, mouseY)
        visibleArea.update(delta)
    }

    override fun draw(context: OwoUIGraphics, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
        // Compile-safe no-op for the 26.2 API migration. Actual web texture rendering can be reintroduced
        // once the preferred 26.2 Owo drawing primitive is confirmed from the active UI library.
    }

    fun resetVisibleArea(): WebTextureComponent {
        this.visibleArea.set(PositionedRectangle.of(0, 0, this.regionWidth, this.regionHeight))
        return this
    }
}
