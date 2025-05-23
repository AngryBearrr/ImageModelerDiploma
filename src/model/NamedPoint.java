package model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Базовый класс точки с единственным общим полем — именем.
 */
public abstract class NamedPoint implements Serializable {
    private final String name;

    public NamedPoint(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    // Сравниваем по имени и по конкретному классу (2D vs 3D)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NamedPoint point = (NamedPoint) o;
        return Objects.equals(name, point.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public abstract String toString();
}
