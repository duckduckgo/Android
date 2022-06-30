const FORM_INPUTS_SELECTOR = `
input:not([type=submit]):not([type=button]):not([type=checkbox]):not([type=radio]):not([type=hidden]):not([type=file]):not([name^=fake i]):not([data-description^=dummy i]),
select`

const SUBMIT_BUTTON_SELECTOR = `
input[type=submit],
input[type=button],
button:not([role=switch]):not([role=link]),
[role=button]`

const email = `
input:not([type])[name*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),
input[type=""][name*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),
input[type=text][name*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]):not([name*=title i]):not([name*=tab i]),
input:not([type])[placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),
input[type=text][placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),
input[type=""][placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),
input:not([type])[placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),
input[type=email],
input[type=text][aria-label*=mail i]:not([aria-label*=search i]),
input:not([type])[aria-label*=mail i]:not([aria-label*=search i]),
input[type=text][placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),
input[name=username][type=email],
input[autocomplete=email]`

// We've seen non-standard types like 'user'. This selector should get them, too
const GENERIC_TEXT_FIELD = `
input:not([type=button]):not([type=checkbox]):not([type=color]):not([type=date]):not([type=datetime-local]):not([type=datetime]):not([type=file]):not([type=hidden]):not([type=month]):not([type=number]):not([type=radio]):not([type=range]):not([type=reset]):not([type=search]):not([type=submit]):not([type=time]):not([type=url]):not([type=week])`

const password = `input[type=password]:not([autocomplete*=cc]):not([autocomplete=one-time-code]):not([name*=answer i]):not([name*=mfa i]):not([name*=tin i])`

const cardName = `
input[autocomplete="cc-name"],
input[autocomplete="ccname"],
input[name="ccname"],
input[name="cc-name"],
input[name="ppw-accountHolderName"],
input[id*=cardname i],
input[id*=card-name i],
input[id*=card_name i]`

const cardNumber = `
input[autocomplete="cc-number"],
input[autocomplete="ccnumber"],
input[autocomplete="cardnumber"],
input[autocomplete="card-number"],
input[name="ccnumber"],
input[name="cc-number"],
input[name*=card i][name*=number i],
input[id*=cardnumber i],
input[id*=card-number i],
input[id*=card_number i]`

const cardSecurityCode = `
input[autocomplete="cc-csc"],
input[autocomplete="csc"],
input[autocomplete="cc-cvc"],
input[autocomplete="cvc"],
input[name="cvc"],
input[name="cc-cvc"],
input[name="cc-csc"],
input[name="csc"],
input[name*=security i][name*=code i]`

const expirationMonth = `
[autocomplete="cc-exp-month"],
[name="ccmonth"],
[name="ppw-expirationDate_month"],
[name=cardExpiryMonth],
[name*=ExpDate_Month i],
[name*=expiration i][name*=month i],
[id*=expiration i][id*=month i]`

const expirationYear = `
[autocomplete="cc-exp-year"],
[name="ccyear"],
[name="ppw-expirationDate_year"],
[name=cardExpiryYear],
[name*=ExpDate_Year i],
[name*=expiration i][name*=year i],
[id*=expiration i][id*=year i]`

const expiration = `
[autocomplete="cc-exp"],
[name="cc-exp"],
[name="exp-date"],
[name="expirationDate"],
input[id*=expiration i]`

const firstName = `
[name*=fname i], [autocomplete*=given-name i],
[name*=firstname i], [autocomplete*=firstname i],
[name*=first-name i], [autocomplete*=first-name i],
[name*=first_name i], [autocomplete*=first_name i],
[name*=givenname i], [autocomplete*=givenname i],
[name*=given-name i],
[name*=given_name i], [autocomplete*=given_name i],
[name*=forename i], [autocomplete*=forename i]`

const middleName = `
[name*=mname i], [autocomplete*=additional-name i],
[name*=middlename i], [autocomplete*=middlename i],
[name*=middle-name i], [autocomplete*=middle-name i],
[name*=middle_name i], [autocomplete*=middle_name i],
[name*=additionalname i], [autocomplete*=additionalname i],
[name*=additional-name i],
[name*=additional_name i], [autocomplete*=additional_name i]`

const lastName = `
[name=lname], [autocomplete*=family-name i],
[name*=lastname i], [autocomplete*=lastname i],
[name*=last-name i], [autocomplete*=last-name i],
[name*=last_name i], [autocomplete*=last_name i],
[name*=familyname i], [autocomplete*=familyname i],
[name*=family-name i],
[name*=family_name i], [autocomplete*=family_name i],
[name*=surname i], [autocomplete*=surname i]`

const fullName = `
[name=name], [autocomplete=name],
[name*=fullname i], [autocomplete*=fullname i],
[name*=full-name i], [autocomplete*=full-name i],
[name*=full_name i], [autocomplete*=full_name i],
[name*=your-name i], [autocomplete*=your-name i]`

const phone = `
[name*=phone i]:not([name*=extension i]):not([name*=type i]):not([name*=country i]), [name*=mobile i]:not([name*=type i]), [autocomplete=tel], [placeholder*="phone number" i]`

const addressStreet1 = `
[name=address], [autocomplete=street-address], [autocomplete=address-line1],
[name=street],
[name=ppw-line1], [name*=addressLine1 i]`

const addressStreet2 = `
[name=address], [autocomplete=address-line2],
[name=ppw-line2], [name*=addressLine2 i]`

const addressCity = `
[name=city], [autocomplete=address-level2],
[name=ppw-city], [name*=addressCity i]`

const addressProvince = `
[name=province], [name=state], [autocomplete=address-level1]`

const addressPostalCode = `
[name=zip], [name=zip2], [name=postal], [autocomplete=postal-code], [autocomplete=zip-code],
[name*=postalCode i], [name*=zipcode i]`

const addressCountryCode = [
    `[name=country], [autocomplete=country],
     [name*=countryCode i], [name*=country-code i],
     [name*=countryName i], [name*=country-name i]`,
    `select.idms-address-country` // Fix for Apple signup
]

const birthdayDay = `
[name=bday-day],
[name=birthday_day], [name=birthday-day],
[name=date_of_birth_day], [name=date-of-birth-day],
[name^=birthdate_d], [name^=birthdate-d],
[aria-label="birthday" i][placeholder="day" i]`

const birthdayMonth = `
[name=bday-month],
[name=birthday_month], [name=birthday-month],
[name=date_of_birth_month], [name=date-of-birth-month],
[name^=birthdate_m], [name^=birthdate-m],
select[name="mm"]`

const birthdayYear = `
[name=bday-year],
[name=birthday_year], [name=birthday-year],
[name=date_of_birth_year], [name=date-of-birth-year],
[name^=birthdate_y], [name^=birthdate-y],
[aria-label="birthday" i][placeholder="year" i]`

const username = [
    `${GENERIC_TEXT_FIELD}[autocomplete^=user]`,
    `input[name=username i]`,
    // fix for `aa.com`
    `input[name="loginId" i]`,
    // fix for https://online.mbank.pl/pl/Login
    `input[name="userID" i]`,
    `input[id="login-id" i]`,
    `input[name=accountname i]`,
    `input[autocomplete=username]`
]

// todo: these are still used directly right now, mostly in scanForInputs
// todo: ensure these can be set via configuration
module.exports.FORM_INPUTS_SELECTOR = FORM_INPUTS_SELECTOR
module.exports.SUBMIT_BUTTON_SELECTOR = SUBMIT_BUTTON_SELECTOR

// Exported here for now, to be moved to configuration later
module.exports.__secret_do_not_use = {
    GENERIC_TEXT_FIELD,
    SUBMIT_BUTTON_SELECTOR,
    FORM_INPUTS_SELECTOR,
    email: email,
    password,
    username,
    cardName,
    cardNumber,
    cardSecurityCode,
    expirationMonth,
    expirationYear,
    expiration,

    firstName,
    middleName,
    lastName,
    fullName,
    phone,
    addressStreet1,
    addressStreet2,
    addressCity,
    addressProvince,
    addressPostalCode,
    addressCountryCode,
    birthdayDay,
    birthdayMonth,
    birthdayYear
}
