package com.gathin.tvhome;

/**
 * A dock with its own number of tiles (local patch, dock_columns). The first {@code dock} positions are the dock,
 * then rows of {@code columns}. The grid has span = lcm(dock, columns) parts: a dock tile takes span/dock, a row
 * tile span/columns. Up and Down between the dock and the first row go to the horizontally nearest tile. With
 * dock == columns it is the plain grid.
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

    /** The first position of the tile's row. */
    static int rowStart(int position, int dock, int columns) {
        return position < dock ? 0 : dock + (position - dock) / columns * columns;
    }

    /** The tile's column within its row. */
    static int column(int position, int dock, int columns) {
        return position - rowStart(position, dock, columns);
    }

    /** How many tiles the row of this position holds, with count tiles in all. */
    static int rowSize(int position, int dock, int columns, int count) {
        int start = rowStart(position, dock, columns);
        return Math.max(0, Math.min(position < dock ? dock : columns, count - start));
    }

    /** The first position of the last row. */
    static int lastRowStart(int count, int dock, int columns) {
        return count <= dock ? 0 : rowStart(count - 1, dock, columns);
    }

    /** The dock tile at the same horizontal place as this one (the position itself if it is in the dock). */
    static int toDock(int position, int dock, int columns) {
        if (position < dock) return position;
        int col = column(position, dock, columns);
        return Math.min(dock - 1, (int) ((col + 0.5f) / columns * dock));
    }

    /** The position above, or -1 from the dock (the system moves on to the top bar). */
    static int up(int position, int dock, int columns) {
        if (position < dock) return -1;
        if (position < dock + columns) return toDock(position, dock, columns);
        return position - columns;
    }

    /** The position below, or -1 when there is nothing below. */
    static int down(int position, int dock, int columns, int count) {
        if (position < dock) {
            if (count <= dock) return -1;
            int col = (int) ((position + 0.5f) / dock * columns);
            return Math.min(dock + col, count - 1);
        }
        if (position + columns < count) return position + columns;
        return rowStart(position, dock, columns) < lastRowStart(count, dock, columns) ? count - 1 : -1;
    }

    /** Whether the tile is at the edge of its row: left at the start or right at the end (rtl mirrors it). */
    static boolean atHorizontalEdge(int position, int rowCount, int dock, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        int col = column(position, dock, columns);
        if (towardStart) return col == 0;
        return col >= rowCount - 1;
    }
}
