package com.example.e_hospital.management.prototype;
import com.example.e_hospital.management.prototype.service.*;
import com.example.e_hospital.management.prototype.data.ModuleCatalog;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DoctorScheduleTests {
    @Test void datedScheduleDrivesSlotsProtectsExistingBookingsAndChecksVersions(){
        var s=new InMemoryHospitalService(new ModuleCatalog());String date=LocalDate.now().plusDays(30).toString();
        var config=new LinkedHashMap<>(s.doctorSchedule("DOC-0001",date));config.putAll(Map.of("startTime","10:00","endTime","12:00","breakStart","11:00","breakEnd","11:30","interval","30","closed","false","location","Synthetic room A"));
        s.saveDoctorSchedule(config,"QA");
        assertThat(s.availableSlots("DOC-0001",date)).containsExactly("10:00","10:30","11:30");
        assertThatThrownBy(()->s.saveDoctorSchedule(config,"QA")).hasMessageContaining("schedule changed");
        var booking=new LinkedHashMap<>(Map.of("name","Synthetic scheduled visit","patient","PAT-0018","doctor","DOC-0001","department","DEP-0001","date",date,"time","10:30","type","Consultation"));
        s.save("appointments",null,booking,"QA");assertThat(s.availableSlots("DOC-0001",date)).doesNotContain("10:30");
        booking.put("time","09:00");assertThatThrownBy(()->s.save("appointments",null,booking,"QA")).hasMessageContaining("configured schedule");
        var closure=new LinkedHashMap<>(s.doctorSchedule("DOC-0001",date));closure.put("closed","true");
        assertThatThrownBy(()->s.saveDoctorSchedule(closure,"QA")).hasMessageContaining("Existing appointments conflict");
        assertThat(s.doctorSchedule("DOC-0001",date).get("closed")).isEqualTo("false");
        var other=new LinkedHashMap<>(s.doctorSchedule("DOC-0002",date));other.put("closed","true");s.saveDoctorSchedule(other,"QA");assertThat(s.availableSlots("DOC-0002",date)).isEmpty();
    }
    @Test void invalidBreakAndIntervalCannotChangeSchedule(){
        var s=new InMemoryHospitalService(new ModuleCatalog());String date=LocalDate.now().plusDays(31).toString();var config=new LinkedHashMap<>(s.doctorSchedule("DOC-0001",date));
        config.put("interval","0");assertThatThrownBy(()->s.saveDoctorSchedule(config,"QA")).hasMessageContaining("Slot length");
        config.put("interval","15");config.put("breakEnd","08:00");assertThatThrownBy(()->s.saveDoctorSchedule(config,"QA")).hasMessageContaining("Break");
        assertThat(s.doctorSchedule("DOC-0001",date).get("version")).isEqualTo("0");
    }
}
