# #!/bin/bash

# echo "Checking environment variables:"
# if [ -z "$SONATYPE_TOKEN" ]; then
#     echo "ERROR: SONATYPE_TOKEN is not set!"
# fi

# echo "Library generation process initiated."

# echo "Generating artifacts for React Native Gradle Plugin."

# # Navigate to the react-native gradle plugin and build and publish
# # cd node_modules/@react-native/gradle-plugin
# ./gradlew build
# ./gradlew publish

# # echo "Applying LibraryCreation Patch."

# # Navigate to the Android folder and apply the patch
# # cd ../../../android
# # git apply libraryCreation.patch

# cd hyperswitch-gradle-plugin
# ./gradlew build
# ./gradlew publish

# cd ..

# # Remove unnecessary files
# # rm app/src/main/java/io/hyperswitch/MainActivity.kt
# # rm app/src/main/res/layout/main_activity.xml
# # rm app/src/main/res/values/styles.xml

# echo "Generating artifacts for Hyperswitch Android SDK."

# # Build and publish the library
# ./gradlew clean
# ./gradlew assembleRelease
# ./gradlew publish

# cd maven/io/hyperswitch || exit 1

# # Function to sign files and create a bundle
# process_version_directory() {
#     local base_dir="$1"
#     local version_dir="$2"
    
#     cd "$version_dir" || return

#     # Sign all relevant files in the version directory
#     for file in *.{aar,pom,jar,module}; do
#         [ -e "$file" ] || continue
        
#         echo "Signing $file..."
#         gpg --armor --output "${file}.asc" --detach-sign "$file"
#     done

#     # # Create the ZIP bundle
#     # local base_name
#     # base_name=$(basename "$base_dir")
#     # local zip_name="${base_name}-$(basename "$version_dir")-bundle.zip"

#     # echo "Creating ZIP bundle for $zip_name..."
#     # zip -r "$zip_name" *.{aar,pom,jar,module} *-sources.jar *-javadoc.jar *.asc

#     cd ..
# }

# upload_to_sonatype() {
#     local zip_file="$1"
#     local sonatype_url="https://central.sonatype.com/api/v1/publisher/upload"
#     local authorization_token="Bearer $SONTAYPE_TOKEN"

#     echo "Uploading $zip_file to Sonatype Central... with key $SONATYPE_TOKEN"

#     # Perform the CURL upload
#     curl --request POST \
#          --verbose \
#          --header "Authorization: $authorization_token" \
#          --form bundle=@"$zip_file" \
#          $sonatype_url
# }

# # Process each library directory
# for library_dir in */; do
#     [ -d "$library_dir" ] || continue
#     cd "$library_dir" || continue
    
#     # Process each version directory
#     for version_dir in */; do
#         [ -d "$version_dir" ] || continue
#         process_version_directory "$library_dir" "$version_dir"
#     done
    
#     cd ..
# done

#  # Create the ZIP bundle
# cd ../../
# local zip_name="hyperswitch-sdk-bundle.zip"
# echo "Creating ZIP bundle for $zip_name..."
# # zip -r "hyperswitch-sdk-bundle.zip" ./*
# zip -r "hyperswitch-sdk-bundle.zip" io/hyperswitch

# upload_to_sonatype "hyperswitch-sdk-bundle.zip"

# echo "Processing completed."


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
cd ../../../hyperswitch-sdk-android
# git apply libraryCreation.patch

cd hyperswitch-gradle-plugin
./gradlew build
./gradlew publish

cd ..
# echo "Generating artifacts for Hyperswitch Android SDK."
# ./gradlew clean
./gradlew assembleRelease
./gradlew publish

cd maven/io/hyperswitch || exit 1

# Function to sign files and create a bundle
process_version_directory() {
    local base_dir="$1"
    local version_dir="$2"
    cd "$version_dir" || return
    
    for file in *.{aar,pom,jar,module}; do
        [ -e "$file" ] || continue
        echo "Signing $file..."
        gpg --armor --output "${file}.asc" --detach-sign "$file"
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
# echo
# echo "Enter the numbers of the libraries you want to include (space-separated)"
# echo "Example: 1 3 4"
# read -p "Your selection: " selections

# # Convert selections to array
# read -ra selection_array <<< "$selections"

# # Validate and process selections
# for selection in "${selection_array[@]}"; do
#     if [[ "$selection" =~ ^[0-9]+$ ]] && ((selection > 0 && selection <= ${#available_libraries[@]})); then
#         selected_libraries+=("${available_libraries[$selection-1]}")
#         echo "Selected: ${available_libraries[$selection-1]}"
#     else
#         echo "Invalid selection: $selection"
#     fi
# done

# # Check if any libraries were selected
# if [ ${#selected_libraries[@]} -eq 0 ]; then
#     echo "No valid libraries selected. Exiting."
#     exit 1
# fi

# Check if SELECTED_LIBRARIES environment variable is set (for CI)
if [ ! -z "$SELECTED_LIBRARIES" ]; then
    echo "Using libraries from SELECTED_LIBRARIES environment variable"
    IFS=',' read -ra selected_libraries <<< "$SELECTED_LIBRARIES"
# Check if command line arguments were provided
elif [ $# -gt 0 ]; then
    echo "Using libraries from command line arguments"
    selected_libraries=("$@")
else
    # Interactive mode
    echo "Available libraries:"
    index=1
    for library in "${available_libraries[@]}"; do
        echo "$index) $library"
        ((index++))
    done

    echo
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