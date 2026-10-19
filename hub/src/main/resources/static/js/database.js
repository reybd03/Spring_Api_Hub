const sqlActions = document.getElementById("sqlActions");
const sqlResults = document.getElementById("sqlResults");

// Get the complete URL
const currentUrl = window.location.href;

//const apiTarget = currentUrl.indexOf("/admin");
const apiTarget = "/admin";
// const apiEndpoint = currentUrl !== -1 ? currentUrl.substring(apiTarget) : currentUrl;
// const apiEndpoint = currentUrl !== 0 ? currentUrl.substring(apiTarget) : currentUrl;

// const startIndex = currentUrl.indexOf(apiTarget) + apiTarget.length;
const apiEndpoint = currentUrl.includes(apiTarget) ? apiTarget : "";


console.log("Endpoint: " + apiEndpoint);


sqlActions.addEventListener("click", async (button) => {
    if (button.target.tagName === "BUTTON") {
        button.preventDefault();
        const form = button.target.closest("form");
        if (form) {
            const formData = new FormData(form);
            
            const data = Object.fromEntries(formData.entries());
            console.log("Data: " + JSON.stringify(data, null, 2));
            console.log("Table Name: " + data.tableName);
            console.log("ID: " + button.target.id + " Target: " + button.target.innerHTML);
            const sqlAction = button.target.id;
            const sqlFunc   = button.target.id;
            const sqlActionPath = apiEndpoint + "/" + sqlAction + "?tableName=" + data.tableName;
            
            if (sqlAction !== null) {
                // fieldElement.textContent = newFieldValue;
                // updateProductField(fieldId, newFieldValue);
                try {
                    const response = await sendSQLAction(sqlActionPath, data, sqlFunc);
                    const htmlText = await response.text();
                    console.log("htmlText: " + htmlText);

                    // sqlResults.querySelector('#results-table').innerText = 'Test'

                    // Rewrite the entire page
                    // document.open();
                    // document.write(htmlText);
                    // document.close();

                    const parser = new DOMParser();
                    const doc = parser.parseFromString(htmlText, "text/html");

                    const errorDiv = doc.querySelector("#results-error")
                    const messageDiv = doc.querySelector("#results-message")
                    const tableDiv = doc.querySelector("#results-table")

                    if (errorDiv) {
                        const errorText = "Error"
                        if (errorDiv.textContent.includes(errorText)) {
                            sqlResults.querySelector("#results-error").innerText = errorDiv.innerText
                        }
                    }
                    if (tableDiv) {
                        const table = tableDiv.querySelector("table");
                        if (!table.rows || table.rows.length === 0){
                            const blankTable = document.createElement("p");
                            blankTable.textContent ="No rows found in Table " + data.tableName;
                            tableDiv.appendChild(blankTable);
                        }
                        sqlResults.querySelector("#results-table").innerText = tableDiv.innerText;
                        
                    }
                    if (messageDiv) {
                        console.log("messageDiv")
                        console.log(messageDiv)
                        sqlResults.querySelector("#results-message").innerText = messageDiv.innerText
                    } else {
                        console.log("Error, Message, and Table div not found");
                    }
                    
                    console.log("errorDiv: " + errorDiv)
                    console.log("messageDiv: " + messageDiv)
                    console.log("tableDiv: " + tableDiv)
                    
                } catch (error) {
                    console.error('Error:', error);
                }
            }
        }
        
        // const fieldElement = document.getElementById(fieldId);
        // const fieldValue = fieldElement.textContent;
        // const newFieldValue = prompt("Enter new value for " + fieldId + ":", fieldValue);
        // button.target.style.display = "none";
        // Needs to group UserName and Password together and require both to be changed together
        
    }
});

async function sendSQLAction(sqlActionPath, data, sqlFunc) {
    console.log("Posting to endpoint: " + sqlActionPath);
    const response = await fetch(sqlActionPath, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
    });
    // .then(response => response.text()
    // )
    // .then(message => {
    //     // 4. Update the UI dynamically without refreshing
    //     // if (sqlFunc === "query") {
    //     //     sqlActions.querySelector("#results-table").innerText = message;
    //     // } else {
    //     //     sqlActions.querySelector("#results-message").innerText = message;
    //     // }
    //     // document.getElementById('responseMessage').innerText = message;
    //     sqlActions.querySelector('#results-error').innerText = 'Submission failed: ' + message;
    //     console.log("Response message: " + message);
        
    // })
    // .catch(error => {
    //     console.error('Error:', error);
    //     // const error_div = document.getElementById('results-error');
    //     // error_div.textContent = 'Submission failed: ' + error;;
    //     sqlActions.querySelector('#results-error').innerText = 'Submission failed: ' + error;
    // });
    return response;
}
