package com.cappleapple.mastery.layout;

import com.cappleapple.mastery.data.NodeAppearance.Shape;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShapeOutlineTest {
    @Test void everyShapeStartsAtTopAndMovesClockwise() {
        for(Shape shape:Shape.values()) {
            assertEquals(0,ShapeOutline.fraction(shape,0,-20,20),1e-9);
            assertEquals(.25,ShapeOutline.fraction(shape,20,0,20),1e-9);
            assertEquals(.5,ShapeOutline.fraction(shape,0,20,20),1e-9);
            assertEquals(.75,ShapeOutline.fraction(shape,-20,0,20),1e-9);
            double previous=-1;
            for(var pixel:ShapeOutline.pixels(shape,20,3)) {
                assertTrue(pixel.fraction()>=previous);previous=pixel.fraction();
                assertTrue(shape.contains(pixel.x()+.5,pixel.y()+.5,20));
                assertFalse(shape.contains(pixel.x()+.5,pixel.y()+.5,17));
            }
        }
        assertEquals(.125,ShapeOutline.fraction(Shape.SQUARE,20,-20,20),1e-9);
    }
    @Test void experienceOutlineUsesCurrentLevelProgressAndFullAtCap() {
        assertEquals(.4,ShapeOutline.experience(40,100,2,10));
        assertEquals(0,ShapeOutline.experience(0,100,2,10));
        assertEquals(1,ShapeOutline.experience(0,100,10,10));
        assertEquals(1,ShapeOutline.experience(300,100,2,10));
        assertEquals(0,ShapeOutline.experience(Double.NaN,100,2,10));
    }
}
