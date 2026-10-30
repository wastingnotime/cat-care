package org.wastingnotime.catcare

/** Stable avatar identifiers shared with the web owner experience. */
enum class CatCoat(val id: String, val label: Int, val color: Long) {
    ORANGE_TABBY("orange-tabby", R.string.coat_orange, 0xffd9913b),
    BROWN_TABBY("brown-tabby", R.string.coat_brown, 0xff806a51),
    GRAY("gray", R.string.coat_gray, 0xff92999d),
    BLACK("black", R.string.coat_black, 0xff303534),
    WHITE("white", R.string.coat_white, 0xfff4f0e6),
    TUXEDO("tuxedo", R.string.coat_tuxedo, 0xff303534),
    CALICO("calico", R.string.coat_calico, 0xfff1e6d1),
    SIAMESE("siamese", R.string.coat_siamese, 0xffe5d4ae);

    companion object {
        fun fromId(id: String): CatCoat =
            entries.firstOrNull { it.id == id } ?: throw CareValidation(CareIssue.COAT)
    }
}
