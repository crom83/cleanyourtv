package com.gathin.tvhome;

/**
 * Док со своим числом плиток (локальная доработка, dock_columns). Первые {@code dock} позиций — док,
 * дальше ряды по {@code columns}. Сетка делится на span = НОК(dock, columns) долей: плитка дока занимает
 * span/dock, плитка ряда span/columns. Вверх и вниз между доком и первым рядом ведут на ближайшую по
 * горизонтали плитку. При dock == columns всё совпадает с обычной сеткой.
 */
final class DockGrid {
    private DockGrid() { }

    static int span(int dock, int columns) {
        int a = dock, b = columns;
        while (b != 0) { int t = a % b; a = b; b = t; }
        return dock / a * columns;
    }

    static int spanSize(int position, int dock, int columns) {
        return span(dock, columns) / (position < dock ? dock : columns);
    }

    /** Первая позиция ряда, в котором стоит плитка. */
    static int rowStart(int position, int dock, int columns) {
        return position < dock ? 0 : dock + (position - dock) / columns * columns;
    }

    /** Колонка плитки внутри её ряда. */
    static int column(int position, int dock, int columns) {
        return position - rowStart(position, dock, columns);
    }

    /** Сколько плиток в ряду этой позиции, при count плитках всего. */
    static int rowSize(int position, int dock, int columns, int count) {
        int start = rowStart(position, dock, columns);
        return Math.max(0, Math.min(position < dock ? dock : columns, count - start));
    }

    /** Первая позиция последнего ряда. */
    static int lastRowStart(int count, int dock, int columns) {
        return count <= dock ? 0 : rowStart(count - 1, dock, columns);
    }

    /** Плитка дока под той же горизонталью, что и эта (сама позиция, если она в доке). */
    static int toDock(int position, int dock, int columns) {
        if (position < dock) return position;
        int col = column(position, dock, columns);
        return Math.min(dock - 1, (int) ((col + 0.5f) / columns * dock));
    }

    /** Позиция выше, или -1 из дока (туда, в верхнюю панель, ведёт система). */
    static int up(int position, int dock, int columns) {
        if (position < dock) return -1;
        if (position < dock + columns) return toDock(position, dock, columns);
        return position - columns;
    }

    /** Позиция ниже, или -1, если ниже ничего нет. */
    static int down(int position, int dock, int columns, int count) {
        if (position < dock) {
            if (count <= dock) return -1;
            int col = (int) ((position + 0.5f) / dock * columns);
            return Math.min(dock + col, count - 1);
        }
        if (position + columns < count) return position + columns;
        return rowStart(position, dock, columns) < lastRowStart(count, dock, columns) ? count - 1 : -1;
    }

    /** Плитка у края своего ряда: влево к началу или вправо к концу (rtl зеркалит). */
    static boolean atHorizontalEdge(int position, int rowCount, int dock, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        int col = column(position, dock, columns);
        if (towardStart) return col == 0;
        return col >= rowCount - 1;
    }
}
