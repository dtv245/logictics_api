package com.company.logicstic.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.fleet.entity.Truck;
import com.company.logicstic.modules.load.entity.Load;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Tests for Load driver share calculation.
 *
 * <p>Business spec: {@code DriverShare = DeliveryCost × GetDriversShareRatio()} where ratio = sum
 * of salaryAmount of main/secondary drivers with SalaryType = "ShareOfGross" and must be 0 ≤ ratio
 * ≤ 1.
 */
class LoadDriverShareTest {

  private Employee createDriver(String salaryType, BigDecimal salaryAmount) {
    Employee driver = new Employee();
    driver.setSalaryType(salaryType);
    driver.setSalaryAmount(salaryAmount);
    driver.setSalaryCurrency("USD");
    return driver;
  }

  private Truck createTruck(Employee main, Employee secondary) {
    Truck truck = new Truck();
    truck.setMainDriver(main);
    truck.setSecondaryDriver(secondary);
    return truck;
  }

  @Test
  void noAssignedTruckShouldReturnZero() {
    Load load = new Load();
    load.setDeliveryCostAmount(new BigDecimal("1000.00"));
    load.setDeliveryCostCurrency("USD");
    assertEquals(BigDecimal.ZERO, load.calcDriverShare());
  }

  @Test
  void noShareOfGrossDriversShouldReturnZero() {
    Employee main = createDriver("Monthly", new BigDecimal("5000.00"));
    Employee secondary = createDriver("Weekly", new BigDecimal("1500.00"));
    Truck truck = createTruck(main, secondary);
    Load load = new Load();
    load.setDeliveryCostAmount(new BigDecimal("1000.00"));
    load.setDeliveryCostCurrency("USD");
    load.setAssignedTruck(truck);

    assertEquals(BigDecimal.ZERO, load.calcDriverShare());
  }

  @Test
  void mainDriverOnlyShareOfGross() {
    Employee main = createDriver("ShareOfGross", new BigDecimal("0.30"));
    Employee secondary = createDriver("Monthly", new BigDecimal("5000.00"));
    Truck truck = createTruck(main, secondary);
    Load load = new Load();
    load.setDeliveryCostAmount(new BigDecimal("1000.00"));
    load.setDeliveryCostCurrency("USD");
    load.setAssignedTruck(truck);

    // DriverShare = 1000 × 0.30 = 300
    assertEquals(new BigDecimal("300.00"), load.calcDriverShare());
  }

  @Test
  void bothDriversShareOfGross() {
    Employee main = createDriver("ShareOfGross", new BigDecimal("0.40"));
    Employee secondary = createDriver("ShareOfGross", new BigDecimal("0.25"));
    Truck truck = createTruck(main, secondary);
    Load load = new Load();
    load.setDeliveryCostAmount(new BigDecimal("2000.00"));
    load.setDeliveryCostCurrency("USD");
    load.setAssignedTruck(truck);

    // Ratio = 0.40 + 0.25 = 0.65
    // DriverShare = 2000 × 0.65 = 1300
    assertEquals(new BigDecimal("1300.00"), load.calcDriverShare());
  }

  @Test
  void ratioExceedsOneShouldThrow() {
    Employee main = createDriver("ShareOfGross", new BigDecimal("0.60"));
    Employee secondary = createDriver("ShareOfGross", new BigDecimal("0.50"));
    Truck truck = createTruck(main, secondary);
    Load load = new Load();
    load.setDeliveryCostAmount(new BigDecimal("1000.00"));
    load.setDeliveryCostCurrency("USD");
    load.setAssignedTruck(truck);

    // Ratio = 1.10 > 1.0 → should throw
    assertThrows(IllegalStateException.class, load::calcDriverShare);
  }

  @Test
  void zeroDeliveryCostShouldReturnZero() {
    Employee main = createDriver("ShareOfGross", new BigDecimal("0.30"));
    Truck truck = createTruck(main, null);
    Load load = new Load();
    load.setDeliveryCostAmount(BigDecimal.ZERO);
    load.setDeliveryCostCurrency("USD");
    load.setAssignedTruck(truck);

    assertEquals(BigDecimal.ZERO, load.calcDriverShare());
  }

  @Test
  void ratioExactlyOneShouldWork() {
    Employee main = createDriver("ShareOfGross", new BigDecimal("1.00"));
    Truck truck = createTruck(main, null);
    Load load = new Load();
    load.setDeliveryCostAmount(new BigDecimal("500.00"));
    load.setDeliveryCostCurrency("USD");
    load.setAssignedTruck(truck);

    // DriverShare = 500 × 1.00 = 500
    assertEquals(new BigDecimal("500.00"), load.calcDriverShare());
  }

  @Test
  void meaningfulSubCentPrecisionIsNotSilentlyRounded() {
    Employee main = createDriver("ShareOfGross", new BigDecimal("0.33"));
    Load load = new Load();
    load.setDeliveryCostAmount(new BigDecimal("100.01"));
    load.setDeliveryCostCurrency("USD");
    load.setAssignedTruck(createTruck(main, null));

    assertEquals(new BigDecimal("33.0033"), load.calcDriverShare());
  }
}
