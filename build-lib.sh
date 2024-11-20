#!/bin/bash

echo "Checking environment variables:"
if [ -z "$SONATYPE_TOKEN" ]; then
    echo "ERROR: SONATYPE_TOKEN is not set!"
fi

echo "Library generation process initiated."
echo "Generating artifacts for React Native Gradle Plugin."

cd ..
cd node_modules/@react-native/gradle-plugin

./gradlew build
./gradlew publish

# echo "Applying LibraryCreation Patch."
# Navigate to the Android folder and apply the patch
cd ../../../android
# cd ../../../hyperswitch-sdk-android
# git apply libraryCreation.patch

cd hyperswitch-gradle-plugin
./gradlew build
./gradlew publish

cd ..
echo "Generating artifacts for Hyperswitch Android SDK."
# ./gradlew clean
./gradlew assembleRelease
./gradlew publish

cd maven/io/hyperswitch || exit 1

export GPG_TTY=$(tty)
echo "RELOADAGENT" | gpg-connect-agent

# Function to sign files and create a bundle
process_version_directory() {
    local base_dir="$1"
    local version_dir="$2"
    cd "$version_dir" || return
    
    for file in *.{aar,pom,jar,module}; do
        [ -e "$file" ] || continue
        echo "Signing $file..."
        # gpg --armor --output "${file}.asc" --detach-sign "$file"
        gpg --batch --pinentry-mode loopback --passphrase "$GPG_PASSPHRASE" --armor --output "${file}.asc" --detach-sign "$file"
    
    done
    cd ..
}

upload_to_sonatype() {
    local zip_file="$1"
    local sonatype_url="https://central.sonatype.com/api/v1/publisher/upload"
    local authorization_token="Bearer $SONATYPE_TOKEN"
    
    echo "Uploading $zip_file to Sonatype Central..."
    curl --request POST \
        --verbose \
        --header "Authorization: $authorization_token" \
        --form bundle=@"$zip_file" \
        $sonatype_url
}

# Get list of available libraries
echo "Discovering available libraries..."
available_libraries=()
index=1
echo "Available libraries:"
for library_dir in */; do
    if [ -d "$library_dir" ]; then
        library_name=${library_dir%/}
        available_libraries+=("$library_name")
        echo "$index) $library_name"
        ((index++))
    fi
done


# Get user selection
selected_libraries=()

# Check if SELECTED_LIBRARIES environment variable is set (for CI)
if [ ! -z "$SELECTED_LIBRARIES" ]; then
    echo "Using libraries from SELECTED_LIBRARIES environment variable"
    IFS=',' read -ra selected_libraries <<< "$SELECTED_LIBRARIES"
else
    # Interactive mode
    echo "Interactive Mode, Available libraries:"
    index=1
    for library in "${available_libraries[@]}"; do
        echo "$index) $library"
        ((index++))
    done

    echo "Enter the numbers of the libraries you want to include (space-separated)"
    echo "Example: 1 3 4"
    read -r selections

    # Convert selections to array
    read -ra selection_array <<< "$selections"

    # Validate and process selections
    for selection in "${selection_array[@]}"; do
        if [[ "$selection" =~ ^[0-9]+$ ]] && ((selection > 0 && selection <= ${#available_libraries[@]})); then
            selected_libraries+=("${available_libraries[$selection-1]}")
             echo "Selected: ${available_libraries[$selection-1]}"
        else
            echo "Invalid selection: $selection"
        fi
    done
fi

# Validate selected libraries
valid_libraries=()
for library in "${selected_libraries[@]}"; do
    if [[ " ${available_libraries[@]} " =~ " ${library} " ]]; then
        valid_libraries+=("$library")
    else
        echo "Warning: Library '$library' not found, skipping"
    fi
done

if [ ${#valid_libraries[@]} -eq 0 ]; then
    echo "No valid libraries selected. Exiting."
    exit 1
fi


echo "Processing selected libraries: ${selected_libraries[*]}"

for library in "${selected_libraries[@]}"; do
    echo "Processing library: $library"
    cd "$library" || continue
    
    # Process each version directory
    for version_dir in */; do
        [ -d "$version_dir" ] || continue
        process_version_directory "$library" "$version_dir"
    done
    cd ..
done



# Create temporary directory for selected libraries
temp_dir=$(mktemp -d)
mkdir -p "$temp_dir/io/hyperswitch"

# Copy only selected libraries
for library in "${selected_libraries[@]}"; do
    echo "Copying library: $library"
    cp -r "$library" "$temp_dir/io/hyperswitch/"
done

# Create the ZIP bundle
cd "$temp_dir"
zip_name="hyperswitch-sdk-bundle.zip"
echo "Creating ZIP bundle for $zip_name..."
zip -r "$zip_name" io/hyperswitch


upload_to_sonatype "hyperswitch-sdk-bundle.zip"
# cp "$zip_name" ../../
cd ../../
rm -rf "$temp_dir"

echo "Processing completed."
