(() => {
    const storeInput = document.querySelector("#storeId");
    const tokenInput = document.querySelector("#accessToken");
    const responseStatus = document.querySelector("#responseStatus");
    const requestLine = document.querySelector("#requestLine");
    const responseBody = document.querySelector("#responseBody");

    function showResponse(method, path, status, body) {
        requestLine.textContent = `${method} ${path}`;
        responseStatus.textContent = status;
        responseStatus.className = `response-status ${status >= 200 && status < 300 ? "is-success" : "is-error"}`;
        responseBody.textContent = body === "" ? "No response body." : JSON.stringify(body, null, 2);
    }

    async function callApi(method, path, body) {
        const token = tokenInput.value.trim();
        if (!token) {
            showResponse(method, path, "TOKEN REQUIRED", { error: { code: "TOKEN_REQUIRED", message: "Enter an access token." } });
            tokenInput.focus();
            return null;
        }

        try {
            const response = await fetch(path, {
                method,
                credentials: "omit",
                cache: "no-store",
                headers: {
                    Authorization: `Bearer ${token}`,
                    ...(body === undefined ? {} : { "Content-Type": "application/json" })
                },
                ...(body === undefined ? {} : { body: JSON.stringify(body) })
            });
            const responseText = await response.text();
            let responseData = responseText;
            if (responseText) {
                try {
                    responseData = JSON.parse(responseText);
                } catch {
                    responseData = responseText;
                }
            }

            showResponse(method, path, response.status, responseData);
            if (response.ok && responseData && typeof responseData === "object") {
                if (responseData.productId) {
                    document.querySelector("#getProductId").value = responseData.productId;
                    document.querySelector("#updateProductId").value = responseData.productId;
                    document.querySelector("#deleteProductId").value = responseData.productId;
                }
                if (Number.isInteger(responseData.stockQuantity)) {
                    document.querySelector("#updateStock").value = responseData.stockQuantity;
                }
                if (Number.isInteger(responseData.version)) {
                    document.querySelector("#expectedVersion").value = responseData.version;
                }
            }
            return responseData;
        } catch {
            showResponse(method, path, "NETWORK ERROR", {
                error: { code: "NETWORK_ERROR", message: "Could not reach the inventory API." }
            });
            return null;
        }
    }

    function inventoryPath(productId) {
        const storeId = encodeURIComponent(storeInput.value.trim());
        return `/api/stores/${storeId}/inventory${productId ? `/${encodeURIComponent(productId.trim())}` : ""}`;
    }

    document.querySelector("#createForm").addEventListener("submit", async (event) => {
        event.preventDefault();
        const productId = document.querySelector("#createProductId").value.trim();
        const stockQuantity = Number(document.querySelector("#createStock").value);
        await callApi("POST", inventoryPath(), { productId, stockQuantity });
    });

    document.querySelector("#getForm").addEventListener("submit", async (event) => {
        event.preventDefault();
        const productId = document.querySelector("#getProductId").value;
        await callApi("GET", inventoryPath(productId));
    });

    document.querySelector("#updateForm").addEventListener("submit", async (event) => {
        event.preventDefault();
        const productId = document.querySelector("#updateProductId").value;
        const stockQuantity = Number(document.querySelector("#updateStock").value);
        const expectedVersion = Number(document.querySelector("#expectedVersion").value);
        await callApi("PUT", `${inventoryPath(productId)}/stock`, { stockQuantity, expectedVersion });
    });

    document.querySelector("#deleteForm").addEventListener("submit", async (event) => {
        event.preventDefault();
        const productId = document.querySelector("#deleteProductId").value;
        const version = Number(document.querySelector("#expectedVersion").value);
        if (!window.confirm(`Delete inventory for ${productId}?`)) {
            return;
        }
        await callApi("DELETE", `${inventoryPath(productId)}?version=${encodeURIComponent(version)}`);
    });
})();