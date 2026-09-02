const productFields = document.getElementById("product-fields");

// Get the complete URL
const currentUrl = window.location.href;

const apiTarget = currentUrl.indexOf("/products");
const apiEndpoint = currentUrl !== -1 ? currentUrl.substring(apiTarget) : currentUrl;
console.log(apiEndpoint);

productFields.addEventListener("click", (button) => {
    if (button.target.tagName === "BUTTON") {
        console.log("ID: " + button.target.id + " Target: " + button.target.innerHTML);
        const fieldId = button.target.id.replace("update", "");
        const fieldElement = document.getElementById(fieldId);
        const fieldValue = fieldElement.textContent;
        const newFieldValue = prompt("Enter new value for " + fieldId + ":", fieldValue);
        // button.target.style.display = "none";
        // Needs to group UserName and Password together and require both to be changed together
        if (newFieldValue !== null) {
            fieldElement.textContent = newFieldValue;
            updateProductField(fieldId, newFieldValue);
        }
    }
});

async function updateProductField(fieldId, fieldValue) {
    // Get the complete URL
    // const currentUrl = window.location.href;
    // const target = "/products";

    // const apiEndpoint = currentUrl.indexOf(target);
    // const result = currentUrl !== -1 ? currentUrl.substring(apiEndpoint) : currentUrl;
    // console.log(result);

    console.log(fieldId, fieldValue);
    const payload = {
        [fieldId]: fieldValue
    };
    console.log(payload);
    fetch(apiEndpoint, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
    }).then(response => {
        // console.log(JSON.stringify(response,null,2)); // console log for full response
        if (!response.ok) {
            throw new Error("Network response was not ok");
        }
        return response.json();
    }).then(data => {
        console.log("Success:", data);
        // alert("Updated successfully");
        // refreshProductFields();
    }).catch(error => {
        console.log("Error message:" + error.message);
        console.log("Error:", error.stack);
        alert("Update failed");
    });
}

// function refreshProductFields(){
//     console.log("Refreshing product fields from URL: " + apiEndpoint);
//     fetch(apiEndpoint + "/pageUpdates").then(response => response.text()).then(data => {
//         console.log("Data: " + data);
//         // document.getElementById("product-fields").innerHTML = data;
//         // alert("Updated successfully");
//     }).catch(error => {
//         console.log("Error message:" + error.message);
//         console.log("Error:", error.stack);
//         alert("Update failed");
//     });
    
// }
