package org.bichitos;

import org.bichitos.application.CsvImporter;
import org.bichitos.domain.Eligibility;
import java.time.LocalDate;

public final class SmokeTest {
    private static int passed=0;
    public static void main(String[] args) {
        check(Eligibility.assess(LocalDate.now().minusYears(20),true,true,false,false,true).state().equals("NO_ELEGIBLE"),"menor de 21");
        check(Eligibility.assess(LocalDate.now().minusYears(30),false,true,false,false,true).state().equals("PENDIENTE"),"documentación pendiente");
        check(Eligibility.assess(LocalDate.now().minusYears(30),true,true,true,false,true).state().equals("PENDIENTE"),"consentimiento pendiente");
        check(Eligibility.assess(LocalDate.now().minusYears(30),true,true,true,true,true).state().equals("ELEGIBLE"),"apto para evaluación humana");
        check(Eligibility.assess(LocalDate.now().minusYears(30),true,true,false,false,false).state().equals("NO_ELEGIBLE"),"sin recursos de cuidado");
        check(CsvImporter.parse("F1;1;\"Ana; María\";x").get(2).equals("Ana; María"),"CSV con separador entre comillas");
        System.out.println("OK: "+passed+" pruebas unitarias sin base de datos");
    }
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;}
}
