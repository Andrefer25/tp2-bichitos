package org.bichitos.domain;

import java.time.LocalDate;
import java.time.Period;

public final class Eligibility {
    private Eligibility() { }

    public record Result(String state, String reason) { }

    public static Result assess(LocalDate birth, boolean identity, boolean address,
                                boolean rents, boolean landlordConsent, boolean care) {
        return assess(birth, Boolean.valueOf(identity), Boolean.valueOf(address),
                Boolean.valueOf(rents), Boolean.valueOf(landlordConsent), Boolean.valueOf(care));
    }

    public static Result assess(LocalDate birth, Boolean identity, Boolean address,
                                Boolean rents, Boolean landlordConsent, Boolean care) {
        if (birth == null || birth.isAfter(LocalDate.now())) return new Result("PENDIENTE", "Fecha de nacimiento inválida");
        if (Period.between(birth, LocalDate.now()).getYears() < 21)
            return new Result("NO_ELEGIBLE", "La persona es menor de 21 años");
        if (care == null) return new Result("PENDIENTE", "Falta declarar tiempo y recursos de cuidado");
        if (!care) return new Result("NO_ELEGIBLE", "No declara tiempo y recursos suficientes");
        if (rents == null) return new Result("PENDIENTE", "Falta declarar si alquila");
        if (!Boolean.TRUE.equals(identity)) return new Result("PENDIENTE", "Falta confirmar identidad");
        if (!Boolean.TRUE.equals(address)) return new Result("PENDIENTE", "Falta confirmar domicilio");
        if (rents && !Boolean.TRUE.equals(landlordConsent)) return new Result("PENDIENTE", "Falta consentimiento del propietario");
        return new Result("ELEGIBLE", "Requisitos mínimos verificados; no garantiza adopción");
    }
}
