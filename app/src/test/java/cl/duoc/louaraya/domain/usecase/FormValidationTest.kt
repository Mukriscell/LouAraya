package cl.duoc.louaraya.domain.usecase

import org.junit.Assert.*
import org.junit.Test

class FormValidationTest {
    @Test fun registrationPasswordAcceptsBothLengthBoundariesAndDoesNotRequireLowercase() {
        FormValidation.registrationPassword("Abcdef1!")
        FormValidation.registrationPassword("A1!" + "a".repeat(17))
        FormValidation.registrationPassword("ABCDEFG1!")
    }

    @Test fun registrationPasswordRejectsValuesOutsideTheEightToTwentyRange() {
        for (password in listOf("", "Abcde1!", "A1!" + "a".repeat(18))) {
            assertEquals(mapOf("password" to "La contraseña debe tener entre 8 y 20 caracteres."), errors {
                FormValidation.registrationPassword(password)
            })
        }
    }

    @Test fun registrationPasswordRequiresUppercaseAndAtLeastOneNumber() {
        assertEquals(mapOf("password" to "Incluye al menos una letra mayúscula."), errors {
            FormValidation.registrationPassword("abcdef1!")
        })
        assertEquals(mapOf("password" to "Incluye al menos un número."), errors {
            FormValidation.registrationPassword("Abcdefg!")
        })
    }

    @Test fun registrationPasswordRequiresPunctuationOrSymbolAndDoesNotCountWhitespaceOrInvisibleCharacters() {
        for (password in listOf("Abcdef12", "Abcdef1 ", "Abcdef1\n", "Abcdef1\u200B")) {
            assertEquals(mapOf("password" to "Incluye al menos un carácter especial, como !, @ o #."), errors {
                FormValidation.registrationPassword(password)
            })
        }
        FormValidation.registrationPassword("Abcdef1¿")
        FormValidation.registrationPassword("Abcdef1€")
    }

    @Test fun stockQuantitiesRequirePositiveWholeUnitsWithoutOverflow() {
        for (value in listOf("", "0", "-1", "1.5", "1,5", "2147483648")) {
            assertEquals(setOf("quantity"), errors { FormValidation.stockQuantity(value) }.keys)
        }
        assertEquals(12, FormValidation.stockQuantity(" 12 "))
    }
    private fun errors(block: () -> Unit): Map<String, String> = try {
        block()
        throw AssertionError("Se esperaba un error de validación")
    } catch (failure: FormValidationException) {
        failure.fields
    }

    @Test fun passwordConfirmationMustMatchExactly() {
        assertEquals(setOf("confirm"), errors { FormValidation.matchingPasswords("Clave2026!", "Clave2026! ") }.keys)
        FormValidation.matchingPasswords("Clave2026!", "Clave2026!")
    }

    @Test fun quotationStepsAndFinalSubmissionShareFieldErrors() {
        val step = errors { FormValidation.quotationPiece("NaN", "Infinity", "", "") }
        val details = FormValidation.quotationDetails("Evento", "NaN", "Infinity", "", "", "2026-12-10")
        val final = errors { FormValidation.quotationPiece(details.widthCm, details.lengthCm, details.material, details.colors) }
        assertEquals(setOf("width", "length", "material", "colors"), step.keys)
        assertEquals(step, final)
    }

    @Test fun decimalCommaDimensionsAreAcceptedAndRetainTheirPrecision() {
        val details = FormValidation.quotationDetails("Evento", " 45,5 ", "70.25", "Algodón", "Natural", "2026-12-10")
        FormValidation.quotationPiece(details.widthCm, details.lengthCm, details.material, details.colors)
        assertEquals(45.5, details.widthCm, 0.00001)
        assertEquals(70.25, details.lengthCm, 0.00001)
    }

    @Test fun invalidDimensionsAndOverlongTextReportTheirOwnFields() {
        assertEquals(setOf("width", "length", "material", "colors"), errors {
            FormValidation.quotationPiece("no-numérico", "0", "a".repeat(101), "a".repeat(101))
        }.keys)
    }

    @Test fun priceAndAgreementRejectDecimalSeparatorsAndOverflow() {
        for (value in listOf("", "45.000", "45,50", "999999999999999999999")) {
            assertEquals(setOf("price"), errors { FormValidation.productPrice(value) }.keys)
            assertEquals(setOf("agreedTotal"), errors { FormValidation.agreedTotal(value) }.keys)
        }
        assertEquals(45000L, FormValidation.productPrice(" 45000 "))
        assertEquals(0L, FormValidation.agreedTotal("0"))
    }
}
