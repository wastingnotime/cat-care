package care.cat.offline;

/** Stable avatar identifiers shared with the web owner experience. */
public enum CatCoat {
  ORANGE_TABBY("orange-tabby", "Rajado laranja", 0xffd9913b),
  BROWN_TABBY("brown-tabby", "Rajado marrom", 0xff806a51),
  GRAY("gray", "Cinza", 0xff92999d),
  BLACK("black", "Preto", 0xff303534),
  WHITE("white", "Branco", 0xfff4f0e6),
  TUXEDO("tuxedo", "Preto e branco", 0xff303534),
  CALICO("calico", "Tricolor", 0xfff1e6d1),
  SIAMESE("siamese", "Siamês", 0xffe5d4ae);
  public final String id, label;
  public final int color;

  CatCoat(String id, String label, int color) {
    this.id = id;
    this.label = label;
    this.color = color;
  }

  public static CatCoat fromId(String id) {
    for (CatCoat coat : values()) if (coat.id.equals(id)) return coat;
    throw new IllegalArgumentException("Escolha uma pelagem válida.");
  }
}
