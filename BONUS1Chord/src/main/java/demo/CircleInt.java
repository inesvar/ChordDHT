package demo;


class CircleInt {
    private int value;
    private int numberBits = Chord.numberBits;

    public CircleInt(int value) {
        //System.out.println("received value "+value+ " " + (1 << numberBits) + " " + (value % (1024)));
        this.value = value % (1 << numberBits);
        //System.out.println("converted value "+this.value);
    }

    public int toInt() {
        return value;
    }

    // Returns true if a <= value <= b in the circle
    // if a == b, returns true
    public boolean isBetween(int a, int b) {
        if (a < b) {
            return a <= value && value <= b;
        } else {
            return a <= value || value <= b;
        }
    }

    // Returns true if a < value <= b in the circle
    public boolean isBetween(CircleInt ca, int b) {
        int a = ca.toInt();
        if (a < b) {
            return a < value && value <= b;
        } else {
            return a < value || value <= b;
        }
    }

    // Returns true if a < value <= b in the circle
    // if a == b, returns true
    public boolean isBetween(int a, CircleInt cb) {
        int b = cb.toInt();
        if (a < b) {
            return a < value && value <= b;
        } else {
            return a < value || value <= b;
        }
    }
    
    // Returns the clockwise distance from other to this in the circle
    public int subtract(CircleInt other) {
        return (value - other.value) % (1 << numberBits);
    }
    
    // Returns the addition of this and other in the circle
    public int add(int other) {
        return (value + other) % (1 << numberBits);
    }
}
