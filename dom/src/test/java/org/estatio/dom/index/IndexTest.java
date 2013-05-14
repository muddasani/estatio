package org.estatio.dom.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.danhaywood.testsupport.jmock.JUnitRuleMockery2;
import com.danhaywood.testsupport.jmock.JUnitRuleMockery2.Mode;

import org.jmock.Expectations;
import org.jmock.auto.Mock;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class IndexTest {

    private LocalDate baseDate;
    private LocalDate nextDate;
    private BigDecimal result;

    private Index index;
    private IndexBase ib1990;
    private IndexBase ib2000;
    private IndexBase ib2010;
    private IndexValue iv1;
    private IndexValue iv2;

    @Mock
    Indices mockIndices;

    @Rule
    public JUnitRuleMockery2 context = JUnitRuleMockery2.createFor(Mode.INTERFACES_AND_CLASSES);

    @Before
    public void setup() {
        baseDate = new LocalDate(2001, 1, 1);
        nextDate = new LocalDate(2011, 1, 1);
        index = new Index();
        index.setIndexService(mockIndices);

        ib1990 = new IndexBase();
        ib1990.setStartDate(new LocalDate(1990, 1, 1));

        ib2000 = new IndexBase();
        ib2000.modifyPreviousBase(ib1990);
        ib2000.setFactor(BigDecimal.valueOf(1.345));
        ib2000.setStartDate(new LocalDate(2000, 1, 1));

        ib2010 = new IndexBase();
        ib2010.modifyPreviousBase(ib2000);
        ib2010.setFactor(BigDecimal.valueOf(1.234));
        ib2010.setStartDate(new LocalDate(2010, 1, 1));

        iv1 = new IndexValue();
        iv1.modifyIndexBase(ib2000);
        iv1.setStartDate(baseDate);
        iv1.setValue(BigDecimal.valueOf(122.2));

        iv2 = new IndexValue();
        iv2.modifyIndexBase(ib2010);
        iv2.setStartDate(nextDate);
        iv2.setValue(BigDecimal.valueOf(111.1));

        result = BigDecimal.valueOf(111.1).divide(BigDecimal.valueOf(122.2), 5, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(1.234));
    }

    @Test
    public void testGetFactorForDate() {
        assertEquals(ib2010.getFactorForDate(baseDate), new BigDecimal("1.234"));
        assertEquals(new BigDecimal("1.659730"), ib2010.getFactorForDate(new LocalDate(1999, 1, 1)));
    }

    @Test
    public void testGetIndexValueForDate() {
        context.checking(new Expectations() {
            {
                oneOf(mockIndices).findIndexValueForDate(with(equal(index)), with(equal(new LocalDate(2001, 1, 1))));
                will(returnValue(iv1));
                oneOf(mockIndices).findIndexValueForDate(with(equal(index)), with(equal(new LocalDate(2011, 1, 1))));
                will(returnValue(iv2));
            }
        });
        assertEquals(BigDecimal.valueOf(122.2), index.getIndexValueForDate(baseDate));
        assertEquals(BigDecimal.valueOf(111.1), index.getIndexValueForDate(nextDate));
    }

    @Test
    public void testGetIndexValueForDateWithNulls() {
        assertNull(index.getIndexValueForDate(null));
    }

    @Test
    public void testGetRebaseFactor() {
        context.checking(new Expectations() {
            {
                oneOf(mockIndices).findIndexValueForDate(with(equal(index)), with(equal(new LocalDate(2011, 1, 1))));
                will(returnValue(iv2));
            }
        });
        assertEquals(BigDecimal.valueOf(1.234), index.getRebaseFactorForDates(baseDate, nextDate));
    }

    @Test
    public void testGetRebaseFactorWithNull() {
        context.checking(new Expectations() {
            {
                oneOf(mockIndices).findIndexValueForDate(with(equal(index)), with(equal(new LocalDate(2011, 1, 1))));
                will(returnValue(null));
            }
        });
        assertEquals(null, index.getRebaseFactorForDates(baseDate, nextDate));
    }
}
