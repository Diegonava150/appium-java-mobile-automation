plugins {
    id("mobile.java-conventions")
}

dependencies {
    // `api`, not `implementation`: tests written against these screens also need
    // the driver types, @MobileTest and Locators that core exposes.
    api(project(":core"))

    implementation(libs.slf4j.api)
}
