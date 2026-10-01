package me.darkz70.quirks.labyrinth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Лабиринт по алгоритму «поиск в глубину» (recursive backtracker).
 * Массив 2*cells+1: ячейки на нечётных индексах, стены — на чётных.
 */
public final class Maze {

    private final int cells;
    private final boolean[][] wall; // true = стена
    private final List<int[]> deadEnds = new ArrayList<>();

    public Maze(int cells, Random random) {
        this.cells = Math.max(3, cells);
        this.wall = new boolean[2 * this.cells + 1][2 * this.cells + 1];
        for (int i = 0; i <= 2 * this.cells; i++) {
            for (int j = 0; j <= 2 * this.cells; j++) {
                wall[i][j] = true;
            }
        }
        generate(random);
        findDeadEnds();
        Collections.shuffle(deadEnds, random);
    }

    private void generate(Random random) {
        boolean[][] visited = new boolean[cells][cells];
        Deque<int[]> stack = new ArrayDeque<>();
        int[] start = {0, 0};
        visited[0][0] = true;
        carve(start);
        stack.push(start);

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            List<int[]> options = new ArrayList<>(4);
            for (int[] d : dirs) {
                int nx = cur[0] + d[0];
                int ny = cur[1] + d[1];
                if (nx >= 0 && ny >= 0 && nx < cells && ny < cells && !visited[nx][ny]) {
                    options.add(new int[]{nx, ny});
                }
            }
            if (options.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] next = options.get(random.nextInt(options.size()));
            // выбиваем стену между текущей и следующей ячейкой
            int wx = (2 * cur[0] + 1 + 2 * next[0] + 1) / 2;
            int wy = (2 * cur[1] + 1 + 2 * next[1] + 1) / 2;
            wall[wx][wy] = false;
            carve(next);
            visited[next[0]][next[1]] = true;
            stack.push(next);
        }
    }

    private void carve(int[] cell) {
        wall[2 * cell[0] + 1][2 * cell[1] + 1] = false;
    }

    /** Тупики — клетки, у которых открыт ровно один проход. Кандидаты под души. */
    private void findDeadEnds() {
        for (int cx = 0; cx < cells; cx++) {
            for (int cy = 0; cy < cells; cy++) {
                int i = 2 * cx + 1;
                int j = 2 * cy + 1;
                int open = 0;
                if (!wall[i + 1][j]) open++;
                if (!wall[i - 1][j]) open++;
                if (!wall[i][j + 1]) open++;
                if (!wall[i][j - 1]) open++;
                if (open == 1 && !(i == 1 && j == 1)) { // вход не считаем тупиком
                    deadEnds.add(new int[]{i, j});
                }
            }
        }
    }

    public int cells() {
        return cells;
    }

    public int arraySize() {
        return 2 * cells + 1;
    }

    /** true = стена на позиции массива (i,j). */
    public boolean isWall(int i, int j) {
        return wall[i][j];
    }

    /** Тупики в координатах массива (нечётные индексы), вход (0,0) исключён. */
    public List<int[]> deadEnds() {
        return deadEnds;
    }
}
