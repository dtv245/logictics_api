package com.company.logicstic.modules.fleet.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.fleet.dto.CreateTruckRequest;
import com.company.logicstic.modules.fleet.dto.TruckView;
import com.company.logicstic.modules.fleet.entity.Truck;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/**
 * Pure unit tests for {@link TruckMapper} using Mappers.getMapper(). No Spring context required —
 * avoids DB dependency. Verifies parity with the old {@code TruckView.from()} logic.
 */
class TruckMapperTest {

  private TruckMapper truckMapper;

  @BeforeEach
  void setUp() {
    truckMapper = Mappers.getMapper(TruckMapper.class);
  }

  private Employee buildDriver(String first, String last) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setFirstName(first);
    e.setLastName(last);
    return e;
  }

  @Test
  void toView_withBothDrivers_flattensDriverFields() {
    Employee main = buildDriver("John", "Doe");
    Employee secondary = buildDriver("Jane", "Smith");

    Truck truck = new Truck();
    truck.setId(UUID.randomUUID());
    truck.setNumber("TRK-001");
    truck.setType("DRY_VAN");
    truck.setVehicleCapacity(26000);
    truck.setStatus("ACTIVE");
    truck.setIsHazmatPlacarded(false);
    truck.setAdrEquipmentIsAdrCertified(false);
    truck.setAdrEquipmentAllowedClasses("");
    truck.setMainDriver(main);
    truck.setSecondaryDriver(secondary);

    TruckView view = truckMapper.toView(truck);

    assertThat(view.mainDriverId()).isEqualTo(main.getId());
    assertThat(view.mainDriverName()).isEqualTo("John Doe");
    assertThat(view.secondaryDriverId()).isEqualTo(secondary.getId());
    assertThat(view.secondaryDriverName()).isEqualTo("Jane Smith");
  }

  @Test
  void toView_withNoDrivers_producesNullDriverFields() {
    Truck truck = new Truck();
    truck.setId(UUID.randomUUID());
    truck.setNumber("TRK-002");
    truck.setType("FLATBED");
    truck.setVehicleCapacity(20000);
    truck.setStatus("INACTIVE");
    truck.setIsHazmatPlacarded(false);
    truck.setAdrEquipmentIsAdrCertified(false);
    truck.setAdrEquipmentAllowedClasses("");
    truck.setMainDriver(null);
    truck.setSecondaryDriver(null);

    TruckView view = truckMapper.toView(truck);

    assertThat(view.mainDriverId()).isNull();
    assertThat(view.mainDriverName()).isNull();
    assertThat(view.secondaryDriverId()).isNull();
    assertThat(view.secondaryDriverName()).isNull();
  }

  /**
   * CreateTruckRequest: number, type, vehicleCapacity, status, make, model, year, vin,
   * licensePlate, licensePlateState, isHazmatPlacarded, mainDriverId, secondaryDriverId,
   * adrEquipmentIsAdrCertified, adrEquipmentAllowedClasses, adrEquipmentOrangePlateNumber
   */
  @Test
  void toEntity_setsDefaultsForNullBooleans() {
    CreateTruckRequest req =
        new CreateTruckRequest(
            "TRK-003",
            "REEFER",
            22000,
            "ACTIVE",
            "Freightliner",
            "Cascadia",
            2022,
            "VIN123",
            "ABC-123",
            "CA",
            null, // isHazmatPlacarded → defaults to false
            null,
            null, // mainDriverId, secondaryDriverId
            null, // adrEquipmentIsAdrCertified → defaults to false
            null, // adrEquipmentAllowedClasses → defaults to ""
            null // adrEquipmentOrangePlateNumber
            );

    Truck entity = truckMapper.toEntity(req);

    assertThat(entity.getIsHazmatPlacarded()).isFalse();
    assertThat(entity.getAdrEquipmentIsAdrCertified()).isFalse();
    assertThat(entity.getAdrEquipmentAllowedClasses()).isEmpty();
    assertThat(entity.getMainDriver()).isNull();
    assertThat(entity.getSecondaryDriver()).isNull();
  }

  @Test
  void toView_matchesOldFromMethodOutput() {
    Employee main = buildDriver("Tom", "Brown");
    Employee sec = buildDriver("Sue", "Green");
    UUID truckId = UUID.randomUUID();

    Truck t = new Truck();
    t.setId(truckId);
    t.setNumber("T-100");
    t.setType("TANKER");
    t.setVehicleCapacity(30000);
    t.setStatus("ACTIVE");
    t.setMake("Volvo");
    t.setModel("VNL");
    t.setYear(2021);
    t.setVin("VIN999");
    t.setLicensePlate("XYZ-789");
    t.setLicensePlateState("TX");
    t.setIsHazmatPlacarded(true);
    t.setMainDriver(main);
    t.setSecondaryDriver(sec);
    t.setAdrEquipmentIsAdrCertified(true);
    t.setAdrEquipmentAllowedClasses("2,3");
    t.setCurrentLocationLatitude(51.5);
    t.setCurrentLocationLongitude(-0.1);

    // Expected from old TruckView.from() logic
    TruckView expected =
        new TruckView(
            truckId,
            "T-100",
            "TANKER",
            30000,
            "ACTIVE",
            "Volvo",
            "VNL",
            2021,
            "VIN999",
            "XYZ-789",
            "TX",
            true,
            main.getId(),
            "Tom Brown",
            sec.getId(),
            "Sue Green",
            true,
            "2,3",
            51.5,
            -0.1);

    TruckView actual = truckMapper.toView(t);
    assertThat(actual).isEqualTo(expected);
  }
}
