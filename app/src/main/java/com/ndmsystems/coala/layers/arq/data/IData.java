package com.ndmsystems.coala.layers.arq.data;

/**
 * Created by Владимир on 16.08.2017.
 * Интерфейс создан для возможности подмены реализации доступа к содержимому передаваемых данных.
 * Одна из возможных реализаций - хранение данных в оперативной памяти, но она недопустима при
 * передаче больших объемов данных, к примеру видео. Поэтому сделана абстракция за которой в
 * будущем можно будет спрятать реализацию экономичную в плане затрат оперативной памяти.
 */

public interface IData {
    /**
     * @param from
     * @param to
     * @return requested bytes
     */
    byte[] get(int from, int to);

    /**
     * @return all bytes
     */
    byte[] get();

    void append(IData data);

    int size();
}
