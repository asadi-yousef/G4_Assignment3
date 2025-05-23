package il.cshaifasweng.OCSFMediatorExample.entities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Catalog implements Serializable {
    private static final long serialVersionUID = -3764891028374651923L;
    private List<Flower> flowers;
    public Catalog() {
        flowers = new ArrayList<Flower>();
    }
    public void addFlower(Flower flower) {
        flowers.add(flower);
    }
    public Flower getFlower(int index) {
        return flowers.get(index);
    }
    public void removeFlower(Flower flower) {
        if (flowers.contains(flower)) {
            flowers.remove(flower);
        }
    }
    public List<Flower> getFlowers() {
        return flowers;
    }
}
