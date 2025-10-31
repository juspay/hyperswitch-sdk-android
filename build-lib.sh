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
./gradlew assembleRelease --continue
./gradlew publish --continue

cd maven/io/hyperswitch || exit 1

export GPG_TTY=$(tty)
echo "RELOADAGENT" | gpg-connect-agent

# Determine interactive vs CI mode
IS_INTERACTIVE=true
if [ ! -z "$SELECTED_LIBRARIES" ]; then
  IS_INTERACTIVE=false
fi

# Function to sign files and create a bundle
process_version_directory() {
    local base_dir="$1"
    local version_dir="$2"
    cd "$version_dir" || return
    
    for file in *.{aar,pom,jar,module}; do
        [ -e "$file" ] || continue
        echo "Signing $file..."
        if [ "$IS_INTERACTIVE" = true ]; then
            gpg --armor --output "${file}.asc" --detach-sign "$file"
        else    
            gpg --batch --pinentry-mode loopback --passphrase "$GPG_PASSPHRASE" --armor --output "${file}.asc" --detach-sign "$file"
        fi
    done
    cd ..
}

# Added format_size function
format_size() {
    local size_bytes=$1
    if (( size_bytes == 0 )); then
        echo "0 B"
        return
    fi
    if (( size_bytes < 1024 )); then
        echo "${size_bytes} B"
    elif (( size_bytes < 1024 * 1024 )); then
        printf "%.2f KB" $(echo "scale=2; $size_bytes / 1024" | bc -l)
    else
        printf "%.2f MB" $(echo "scale=2; $size_bytes / (1024 * 1024)" | bc -l)
    fi
}

MAVEN_CENTRAL_BASE_URL="https://repo1.maven.org/maven2"

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

        # Check for .aar files (Android artifacts) in any version subdirectory, this is to remove the libraries that don't have any android artifacts
        has_android_artifacts=false
        for version_dir in "$library_dir"/*/; do
            if [ -d "$version_dir" ] && ls "$version_dir"/*.aar >/dev/null 2>&1; then
                has_android_artifacts=true
                break
            fi
        done

        if [ "$has_android_artifacts" = true ]; then
            available_libraries+=("$library_name")
            echo "$index) $library_name"
            ((index++))
        else
            echo "Skipping directory (no .aar files found): $library_name"
        fi
    fi
done


# Get user selection
selected_libraries=()

# FOR LOCAL TESTING: Hardcoding SELECTED_LIBRARIES
# Make sure to comment this out or remove for CI/production use.
# SELECTED_LIBRARIES="hyperswitch-sdk-android,hyperswitch-sdk-android-lite,hyperswitch-gradle-plugin,react-native-hyperswitch-netcetera-3ds,react-native-hyperswitch-scancard,react-native-inappbrowser-reborn,react-native-klarna-inapp-sdk,react-native-svg,sentry_react-native,react-native-hyperswitch-samsung-pay"
# echo "INFO: LOCAL TESTING OVERRIDE - SELECTED_LIBRARIES forced to: $SELECTED_LIBRARIES"

# Check if SELECTED_LIBRARIES environment variable is set (for CI or manual override)
if [ "$IS_INTERACTIVE" = false ]; then
    echo "Using libraries from SELECTED_LIBRARIES environment variable: $SELECTED_LIBRARIES"
   if [[ "$SELECTED_LIBRARIES" == "all" ]]; then
        echo "'all' keyword detected. Using all discovered available libraries."
        selected_libraries=("${available_libraries[@]}") # Copy all available libraries
        echo "Selected all ${#selected_libraries[@]} libraries: ${selected_libraries[*]}"  # Add this line
   else
       IFS=',' read -ra selected_libraries <<< "$SELECTED_LIBRARIES"
   fi
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
    cd "$library" || { echo "Failed to cd into $library directory. Skipping."; continue; }


    echo
    echo "############################################################"
    echo "##########   AAR Size Comparison Report: $library 🤖   ##########"
    echo "############################################################"

    temp_version_dirs=()
    for dir_name_with_slash in */; do
        if [ -d "$dir_name_with_slash" ]; then
            dirname_no_slash=${dir_name_with_slash%/}
            if echo "$dirname_no_slash" | grep -qE '^[0-9]+(\.[0-9]+)*$'; then
                temp_version_dirs+=("$dirname_no_slash")
            fi
        fi
    done

    if [ ${#temp_version_dirs[@]} -gt 0 ]; then
        current_build_version_dirs=($(printf "%s\\n" "${temp_version_dirs[@]}" | sort -V))
    else
        current_build_version_dirs=()
    fi

    if [ ${#current_build_version_dirs[@]} -eq 0 ]; then
        echo "No new version directory found locally for $library. Cannot determine new version."
    else
        # Get the last element for new_version, compatible with older Bash
        if [ ${#current_build_version_dirs[@]} -gt 0 ]; then
            last_index=$((${#current_build_version_dirs[@]} - 1))
            new_version="${current_build_version_dirs[$last_index]}"
        else
            new_version="" # Should not happen if outer if is true, but defensive
        fi

        new_aar_file_name="${library}-${new_version}.aar"
        new_aar_path="${new_version}/${new_aar_file_name}" # Relative to $library dir in maven/io/hyperswitch

        if [ ! -f "$new_aar_path" ]; then
            echo "- New AAR File: $new_aar_path (Not Found locally after build)"
        else
            new_aar_size_bytes=$(wc -c < "$new_aar_path" | awk '{print $1}')
            new_aar_size_formatted=$(format_size $new_aar_size_bytes)
            echo "- New Version (current build): $new_version"

            old_aar_size_bytes=0
            old_aar_size_formatted="N/A"
            old_version_found_and_fetched=false
            old_version=""


            metadata_url="${MAVEN_CENTRAL_BASE_URL}/io/hyperswitch/${library}/maven-metadata.xml"
            echo "  Fetching metadata from: $metadata_url"

            metadata_content=$(curl -sSL --retry 3 --retry-delay 5 "$metadata_url")
            
            if [ -z "$metadata_content" ]; then
                echo "  Failed to fetch maven-metadata.xml for $library. Cannot determine old version."
            else
                # Try to extract from <latest> tag using a more robust sed pattern
                old_version=$(echo "$metadata_content" | sed -n 's#.*<latest>\(.*\)<\/latest>.*#\1#p')
                
                if [ -z "$old_version" ]; then
                    echo "  Could not find <latest> tag in metadata using sed -n 's#.*<latest>\\(.*\\)<\\/latest>.*#\\1#p'. Trying to find highest version from <versions>."
                    all_versions=$(echo "$metadata_content" | grep '<version>' | sed -e 's#.*<version>##' -e 's#<\\/version>.*##' | sort -V | uniq)
                    if [ -n "$all_versions" ]; then
                        temp_old_version=$(echo "$all_versions" | grep -v "^${new_version}$" | tail -n 1)
                        if [ -n "$temp_old_version" ]; then
                            old_version=$temp_old_version
                        else 
                            # if new_version was the only one or the highest, try the one below that.
                            old_version=$(echo "$all_versions" | head -n -1 | tail -n 1)
                        fi
                    fi
                fi

                if [ -z "$old_version" ]; then
                    echo "  Could not determine old version from maven-metadata.xml for $library."
                fi
            fi

            if [ -n "$old_version" ] && [ "$old_version" != "$new_version" ]; then
                echo "- Old Version (from Maven Central): $old_version"
                old_aar_file_name_remote="${library}-${old_version}.aar"
                download_url="${MAVEN_CENTRAL_BASE_URL}/io/hyperswitch/${library}/${old_version}/${old_aar_file_name_remote}"
                temp_download_dir="/tmp/old_aar_download_${library}_${old_version}"
                mkdir -p "$temp_download_dir"
                downloaded_aar_path="${temp_download_dir}/${old_aar_file_name_remote}"

                echo "  Downloading from: $download_url"
                curl -fSL --retry 3 --retry-delay 5 -o "$downloaded_aar_path" "$download_url"
                
                if [ -f "$downloaded_aar_path" ] && [ $(wc -c < "$downloaded_aar_path" | awk '{print $1}') -gt 0 ]; then
                    echo "  Successfully downloaded old AAR to $downloaded_aar_path"
                    old_aar_size_bytes=$(wc -c < "$downloaded_aar_path" | awk '{print $1}')
                    old_aar_size_formatted=$(format_size $old_aar_size_bytes)
                    old_version_found_and_fetched=true
                else
                    echo "  Failed to download old AAR for $library version $old_version from $download_url."
                    if [ -f "$downloaded_aar_path" ]; then rm "$downloaded_aar_path"; fi # Clean up partial download
                fi
                rm -rf "$temp_download_dir"
            elif [ "$old_version" == "$new_version" ]; then
                 echo "(Skipping comparison as old version from metadata is same as new version)"
            else
                 echo "(Could not determine or fetch old version for comparison)"
            fi
            
            if $old_version_found_and_fetched; then
                echo "- Old AAR Size: $old_aar_size_formatted"
                echo "- New AAR Size: $new_aar_size_formatted"
                echo "----------------------------------------------"
                size_diff_kb=$(echo "scale=2; ($new_aar_size_bytes - $old_aar_size_bytes) / 1024" | bc -l)
                echo "- Size Difference: ${size_diff_kb} KB"
                echo "----------------------------------------------"
               

                # Check for significant size increase (20% or more)
                if [ "$old_aar_size_bytes" -gt 0 ]; then # Avoid division by zero
                    percentage_increase=$(echo "scale=2; (($new_aar_size_bytes - $old_aar_size_bytes) * 100) / $old_aar_size_bytes" | bc -l)
                    is_significant_increase=$(echo "$percentage_increase >= 20" | bc -l)
                    if [ "$is_significant_increase" -eq 1 ]; then
                        echo
                        echo -e "\033[1;31m!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\033[0m"
                        echo -e "\033[1;31m!!                    SIZE INCREASE WARNING                   !!\033[0m"
                        echo -e "\033[1;31m!!          AAR size increased by ${percentage_increase}%              !!\033[0m"
                        echo -e "\033[1;31m!!          (Old: $old_aar_size_formatted, New: $new_aar_size_formatted)             !!\033[0m"
                        echo -e "\033[1;31m!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\033[0m"
                        echo
                    fi
                elif [ "$new_aar_size_bytes" -gt 0 ]; then 
                    echo
                    echo -e "\033[1;31m!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\033[0m"
                    echo -e "\033[1;31m!!                    SIZE INCREASE WARNING                   !!\033[0m"
                    echo -e "\033[1;31m!!          AAR size increased from 0 B to $new_aar_size_formatted         !!\033[0m"
                    echo -e "\033[1;31m!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\033[0m"
                    echo
                fi
            else
                echo "- New AAR Size: $new_aar_size_formatted" 
            fi
        fi
    fi
    echo "############################################################"
    echo

    echo "Initiating signing process for versions in $library..."
    for version_dir_name in */; do 
        if [ -d "$version_dir_name" ]; then
            # process_version_directory expects the version string (directory name without /)
            process_version_directory "$library" "${version_dir_name%/}"
        fi
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
